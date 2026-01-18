package com.example.barterchest;

import com.example.barterchest.command.BarterChestCommand;
import com.example.barterchest.config.BarterConfig;
import com.example.barterchest.integration.SimpleClaimsIntegration;
import com.example.barterchest.interaction.BarterLicenseInteraction;
import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.system.BarterBreakProtectionSystem;
import com.example.barterchest.system.BarterChestMergeProtectionSystem;
import com.example.barterchest.system.BarterInteractSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * BarterChest Plugin
 * 
 * A player shop system that allows players to create chest-based shops
 * for buying and selling items using configurable barter currencies.
 */
public class BarterChestPlugin extends JavaPlugin {
    
    private static final HytaleLogger LOGGER = HytaleLogger.get("BarterChest");
    
    private static BarterChestPlugin instance;
    
    public static BarterChestPlugin get() {
        return instance;
    }
    
    public BarterChestPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
    
    @Override
    protected void setup() {
        instance = this;
        LOGGER.at(Level.INFO).log("Setting up BarterChest plugin...");
        
        // Load configuration
        BarterConfig.load();
        
        // Initialize optional SimpleClaims integration
        SimpleClaimsIntegration.initialize();
        
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
    }
    
    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("Shutting down BarterChest plugin...");
        instance = null;
    }
}
