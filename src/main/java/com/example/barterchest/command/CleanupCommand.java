package com.example.barterchest.command;

import com.example.barterchest.state.BarterChestBlockState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subcommand: /barterchest cleanup
 * 
 * Removes the nearest non-pickupable floating item within 3 blocks of the player.
 * Used to clean up orphaned shop display items.
 */
public class CleanupCommand extends AbstractAsyncCommand {
    
    private static final double MAX_DISTANCE = 3.0;
    
    public CleanupCommand() {
        super("cleanup", "Remove nearest floating display item (within 3 blocks)");
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
        
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || !playerRef.isValid()) {
            context.sendMessage(Message.raw("You must be in a world!"));
            return CompletableFuture.completedFuture(null);
        }
        
        Store<EntityStore> store = playerRef.getStore();
        World world = store.getExternalData().getWorld();
        
        // Run on the world thread to avoid threading issues
        return CompletableFuture.runAsync(() -> {
            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) {
                player.sendMessage(Message.raw("Could not get your position!").color(Color.RED));
                return;
            }
            
            double playerX = playerTransform.getPosition().getX();
            double playerY = playerTransform.getPosition().getY();
            double playerZ = playerTransform.getPosition().getZ();
            
            // Find the nearest non-pickupable item
            AtomicReference<Ref<EntityStore>> nearestRef = new AtomicReference<>(null);
            AtomicReference<Double> nearestDistSq = new AtomicReference<>(MAX_DISTANCE * MAX_DISTANCE);
            
            // Query for entities with PreventPickup component
            store.forEachChunk((Query) PreventPickup.getComponentType(), (chunk, cmdBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                    if (transform == null) continue;
                    
                    double ex = transform.getPosition().getX();
                    double ey = transform.getPosition().getY();
                    double ez = transform.getPosition().getZ();
                    
                    double dx = ex - playerX;
                    double dy = ey - playerY;
                    double dz = ez - playerZ;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    
                    if (distSq < nearestDistSq.get()) {
                        nearestDistSq.set(distSq);
                        nearestRef.set(chunk.getReferenceTo(i));
                    }
                }
            });
            
            Ref<EntityStore> toRemove = nearestRef.get();
            if (toRemove != null && toRemove.isValid()) {
                try {
                    store.removeEntity(toRemove, RemoveReason.REMOVE);
                    double dist = Math.sqrt(nearestDistSq.get());
                    player.sendMessage(Message.raw(String.format("Removed floating item %.1f blocks away", dist)).color(Color.GREEN));
                } catch (Exception e) {
                    player.sendMessage(Message.raw("Failed to remove item: " + e.getMessage()).color(Color.RED));
                }
            } else {
                player.sendMessage(Message.raw("No floating items found within " + (int)MAX_DISTANCE + " blocks").color(Color.YELLOW));
            }
            
        }, world);  // Execute on the world's executor
    }
}
