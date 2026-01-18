package com.example.barterchest.command;

import com.example.barterchest.admin.AdminModeManager;
import com.example.barterchest.state.BarterChestBlockState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand: /barterchest admin
 * 
 * Toggles admin mode for shop management.
 * When in admin mode, admins can access any shop's config UI.
 * When not in admin mode, admins interact with shops like regular customers.
 */
public class AdminCommand extends AbstractAsyncCommand {
    
    public AdminCommand() {
        super("admin", "Toggle admin mode for shop management");
        requirePermission(BarterChestBlockState.ADMIN_PERMISSION);
    }
    
    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players!"));
            return CompletableFuture.completedFuture(null);
        }
        
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            context.sendMessage(Message.raw("You must be in a world!"));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        
        return CompletableFuture.runAsync(() -> {
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            
            if (playerRef == null) {
                context.sendMessage(Message.raw("Error getting player info!"));
                return;
            }
            
            boolean enabled = AdminModeManager.toggleAdminMode(playerRef.getUuid());
            
            if (enabled) {
                player.sendMessage(Message.raw("Admin mode ENABLED - You can now manage any shop").color(Color.GREEN).bold(true));
            } else {
                player.sendMessage(Message.raw("Admin mode DISABLED - You now interact as a customer").color(Color.ORANGE).bold(true));
            }
        }, world);
    }
}
