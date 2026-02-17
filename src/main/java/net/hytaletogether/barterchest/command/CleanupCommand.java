package net.hytaletogether.barterchest.command;

import net.hytaletogether.barterchest.state.BarterChestBlockState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand: /barterchest cleanup
 *
 * Removes the closest orphaned display item within 3 blocks of the admin.
 * This is useful for cleaning up "ghost" items that were left behind if a shop
 * was removed via WorldEdit or other means without triggering the plugin's removal logic.
 */
public class CleanupCommand extends AbstractAsyncCommand {

    public CleanupCommand() {
        super("cleanup", "Removes the closest display item within 3 blocks.");
        requirePermission(BarterChestBlockState.ADMIN_PERMISSION);
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext context) {
        CommandSender sender = context.sender();
        if (!(sender instanceof Player player)) {
            context.sendMessage(Message.raw("This command can only be used by players."));
            return CompletableFuture.completedFuture(null);
        }

        World world = player.getWorld();
        if (world == null) {
            context.sendMessage(Message.raw("Could not access the world.").color(Color.RED));
            return CompletableFuture.completedFuture(null);
        }

        // All Store/World access must be done on the main thread.
        // Schedule the work to run on the world thread to avoid thread safety issues.
        return CompletableFuture.runAsync(() -> {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();

            // Get player position
            Ref<EntityStore> playerRef = player.getReference();
            if (playerRef == null || !playerRef.isValid()) {
                player.sendMessage(Message.raw("Could not get your position.").color(Color.RED));
                return;
            }
            TransformComponent playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (playerTransform == null) {
                player.sendMessage(Message.raw("Could not get your position.").color(Color.RED));
                return;
            }
            Vector3d playerPos = playerTransform.getPosition();

            double closestDistSq = 3.0 * 3.0; // 3 blocks radius squared
            Ref<EntityStore> closestEntity = null;

            // Since Hytale API doesn't provide a direct way to iterate all entities,
            // we need to use a different approach. We access the entity store's
            // internal entity maps using reflection to find entities within range.
            
            try {
                // Access the entitiesByUuid map from EntityStore
                Field entitiesByUuidField = entityStore.getClass().getDeclaredField("entitiesByUuid");
                entitiesByUuidField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<UUID, Ref<EntityStore>> entitiesByUuid = 
                    (Map<UUID, Ref<EntityStore>>) entitiesByUuidField.get(entityStore);
                
                // Iterate through all entities in the world
                for (Ref<EntityStore> entityRef : entitiesByUuid.values()) {
                    if (!entityRef.isValid()) continue;
                    
                    // Check if this is a display item entity (has ItemComponent + PreventPickup)
                    ItemComponent itemComponent = store.getComponent(entityRef, ItemComponent.getComponentType());
                    PreventPickup preventPickup = store.getComponent(entityRef, PreventPickup.getComponentType());
                    
                    if (itemComponent != null && preventPickup != null) {
                        // This is a floating display item (can't be picked up)
                        TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                        if (transform != null) {
                            Vector3d pos = transform.getPosition();
                            double dx = pos.getX() - playerPos.getX();
                            double dy = pos.getY() - playerPos.getY();
                            double dz = pos.getZ() - playerPos.getZ();
                            double distSq = dx * dx + dy * dy + dz * dz;
                            
                            if (distSq < closestDistSq) { // Use < to find the absolute closest
                                closestDistSq = distSq;
                                closestEntity = entityRef;
                            }
                        }
                    }
                }
                
                if (closestEntity != null) {
                    // Use removeEntity and the correct RemoveReason
                    store.removeEntity(closestEntity, RemoveReason.REMOVE);
                    player.sendMessage(Message.raw("Removed closest display entity.").color(Color.GREEN));
                } else {
                    player.sendMessage(Message.raw("No display entities found within 3 blocks.").color(Color.YELLOW));
                }
                
            } catch (Exception e) {
                player.sendMessage(Message.raw("An error occurred while cleaning up entities: " + e.getMessage()).color(Color.RED));
                e.printStackTrace();
            }
        }, world);
    }
}
