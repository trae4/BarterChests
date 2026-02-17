package net.hytaletogether.barterchest.system;

import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Collections;
import java.util.Set;

/**
 * System that prevents players from placing blocks in a 3x3x3 cube around shop chests.
 * 
 * This prevents:
 * 1. Chest merging (double-chest formation) which would break the shop
 * 2. Connected block updates from corrupting the shop's BlockState
 * 3. Any exploit involving diagonal chest placement and merging
 * 
 * Players should complete their build before registering a chest as a shop.
 */
public class BarterChestMergeProtectionSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
    
    public BarterChestMergeProtectionSystem() {
        super(PlaceBlockEvent.class);
    }
    
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {
        
        // If already cancelled by another system, skip
        if (event.isCancelled()) {
            return;
        }
        
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        
        if (player == null) {
            return;
        }
        
        // Get the target position
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            return;
        }
        
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        
        int x = targetBlock.getX();
        int y = targetBlock.getY();
        int z = targetBlock.getZ();

        // Fast-path: if this placement position is protected (shop neighbor), block.
        if (ProtectedShopIndex.get().isProtected(world, x, y, z)) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Cannot place blocks near a shop chest!").color(Color.RED));
            return;
        }

        // If the block being placed is itself a shop chest (rare, but possible in edge cases)
        // warm the index without causing chunk loads.
        BlockState existing = world.getState(x, y, z, false);
        if (existing instanceof BarterChestBlockState) {
            boolean added = net.hytaletogether.barterchest.system.ShopLocationRegistry
                    .ensureRegisteredAndReturnAdded(world.getName(), x, y, z);
            if (added) {
                ProtectedShopIndex.get().addShop(world, x, y, z);
            }
            event.setCancelled(true);
            player.sendMessage(Message.raw("Cannot place blocks near a shop chest!").color(Color.RED));
        }
    }
    
    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
    
    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
