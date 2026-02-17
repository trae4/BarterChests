package net.hytaletogether.barterchest;

import net.hytaletogether.barterchest.command.BarterChestCommand;
import net.hytaletogether.barterchest.config.BarterConfig;
import net.hytaletogether.barterchest.display.BarterDisplayManager;
import net.hytaletogether.barterchest.integration.SimpleClaimsIntegration;
import net.hytaletogether.barterchest.interaction.BarterLicenseInteraction;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.BarterBreakProtectionSystem;
import net.hytaletogether.barterchest.system.BarterChestMergeProtectionSystem;
import net.hytaletogether.barterchest.system.BarterInteractSystem;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry;
import net.hytaletogether.barterchest.system.ShopOwnershipRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * BarterChest Plugin
 * 
 * A player shop system that allows players to create chest-based shops
 * for buying and selling items using configurable barter currencies.
 */
public class BarterChestPlugin extends JavaPlugin {
    
    private static final HytaleLogger LOGGER = HytaleLogger.get("BarterChest");
    
    /** Display refresh interval in minutes */
    private static final long DISPLAY_REFRESH_INTERVAL_MINUTES = 3;
    
    private static BarterChestPlugin instance;
    
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> displayRefreshTask;
    
    public static BarterChestPlugin get() {
        return instance;
    }

    /**
     * Some helper utilities need to log without holding onto a plugin instance.
     *
     * <p><b>Important:</b> This must NOT be named {@code getLogger()} because the base plugin
     * class already defines {@code getLogger()} as an <i>instance</i> method. A static method
     * with the same signature triggers a compiler error.
     */
    public static HytaleLogger getPluginLogger() {
        return LOGGER;
    }
    
    public BarterChestPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
    
    @Override
    protected void setup() {
        instance = this;
        LOGGER.at(Level.INFO).log("Setting up BarterChest plugin...");
        
        try {
            // Load configuration
            BarterConfig.load();

            // Load persistent shop ownership counts (for per-player shop limits)
            ShopOwnershipRegistry.load();

            // Load known shop locations and warm the neighbor-protection index.
            // This prevents block edits around existing shops from corrupting the custom container state
            // immediately after a restart, without doing any world scanning.
            ShopLocationRegistry.loadAndWarmProtectionIndex();

            // Load metadata (owner + listing info) used by admin tooling.
            ShopMetadataRegistry.load();
            
            // Initialize optional SimpleClaims integration
            SimpleClaimsIntegration.initialize();
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).withCause(e).log("Failed to set up BarterChest plugin. Disabling.");
            // NOTE: We don't have a way to disable the plugin at this stage,
            // but we can prevent it from registering its systems and commands.
            return;
        }
        
        // Register the barter chest block state
        getBlockStateRegistry().registerBlockState(
            BarterChestBlockState.class,
            "BarterChest",
            BarterChestBlockState.CODEC
        );
        
        // Register the barter license interaction type
        getCodecRegistry(Interaction.CODEC).register(
            "BarterChest_LicenseInteraction",
            BarterLicenseInteraction.class,
            BarterLicenseInteraction.CODEC
        );
        
        // Register the shop interact system (handles right-click on shops)
        getEntityStoreRegistry().registerSystem(new BarterInteractSystem());
        
        // Register the shop break protection systems
        getEntityStoreRegistry().registerSystem(new BarterBreakProtectionSystem());
        getEntityStoreRegistry().registerSystem(new BarterBreakProtectionSystem.BreakBlockProtectionSystem());
        
        // Register the chest merge protection system
        getEntityStoreRegistry().registerSystem(new BarterChestMergeProtectionSystem());
        
        // Register the barterchest command
        getCommandRegistry().registerCommand(new BarterChestCommand());
        
        LOGGER.at(Level.INFO).log("BarterChest plugin setup complete");
    }
    
    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("BarterChest plugin started!");
        
        // Start the display refresh scheduler
        startDisplayRefreshScheduler();
    }
    
    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("Shutting down BarterChest plugin...");
        
        // Stop the display refresh scheduler
        stopDisplayRefreshScheduler();
        
        instance = null;
    }
    
    /**
     * Start the periodic display refresh task.
     * Runs every 10 minutes to restore floating items removed by chunk cleanup.
     */
    private void startDisplayRefreshScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BarterChest-DisplayRefresh");
            t.setDaemon(true);
            return t;
        });
        
        // Initial delay of 2 minutes (let server fully load), then every 10 minutes
        displayRefreshTask = scheduler.scheduleAtFixedRate(
            () -> {
                try {
                    BarterDisplayManager.refreshAllDisplays();
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Error during display refresh");
                }
            },
            2, // initial delay
            DISPLAY_REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        
        LOGGER.at(Level.INFO).log("Display refresh scheduler started (every %d minutes)", 
            DISPLAY_REFRESH_INTERVAL_MINUTES);
    }
    
    /**
     * Stop the display refresh scheduler.
     */
    private void stopDisplayRefreshScheduler() {
        if (displayRefreshTask != null) {
            displayRefreshTask.cancel(false);
            displayRefreshTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        LOGGER.at(Level.INFO).log("Display refresh scheduler stopped");
    }
    
    /**
     * Manually trigger a display refresh (for admin commands).
     */
    public void triggerDisplayRefresh() {
        if (scheduler != null) {
            scheduler.execute(() -> {
                try {
                    BarterDisplayManager.refreshAllDisplays();
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("Error during manual display refresh");
                }
            });
        }
    }
}
