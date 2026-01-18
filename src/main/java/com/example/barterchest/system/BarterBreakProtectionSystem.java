package com.example.barterchest.system;

import com.example.barterchest.state.BarterChestBlockState;
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
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
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
 * System that prevents shop blocks and nearby blocks from being damaged/broken.
 * 
 * Protects:
 * 1. The shop chest itself
 * 2. All blocks in a 3x3x3 cube around the shop (to prevent connected block updates)
 * 
 * Only the shop owner can remove their shop (via the GUI remove button).
 */
public class BarterBreakProtectionSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {
    
    public BarterBreakProtectionSystem() {
        super(DamageBlockEvent.class);
    }
    
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                       @Nonnull DamageBlockEvent event) {
        
        if (event.isCancelled()) {
            return;
        }
        
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        
        if (player == null) {
            return;
        }
        
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        
        // Get the target block position
        Vector3i targetBlock = event.getTargetBlock();
        if (targetBlock == null) {
            return;
        }
        
        int x = targetBlock.getX();
        int y = targetBlock.getY();
        int z = targetBlock.getZ();
        
        // Check if target block itself is a shop
        BlockState targetState = world.getState(x, y, z, true);
        if (targetState instanceof BarterChestBlockState) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Shop chests cannot be broken! Use the shop menu to remove.").color(Color.RED));
            return;
        }
        
        // Check all 26 blocks in a 3x3x3 cube around the target
        // If any is a shop, block the break
        if (isNearShop(world, x, y, z)) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Cannot break blocks near a shop chest!").color(Color.RED));
        }
    }
    
    /**
     * Check if a position is within a 3x3x3 cube of any shop.
     */
    private boolean isNearShop(World world, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue; // Skip center (already checked)
                    }
                    
                    try {
                        BlockState state = world.getState(x + dx, y + dy, z + dz, true);
                        if (state instanceof BarterChestBlockState) {
                            return true;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        return false;
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
    
    /**
     * Inner system to also handle BreakBlockEvent (instant break / creative mode).
     */
    public static class BreakBlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        
        public BreakBlockProtectionSystem() {
            super(BreakBlockEvent.class);
        }
        
        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                           @Nonnull BreakBlockEvent event) {
            
            if (event.isCancelled()) {
                return;
            }
            
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            Player player = store.getComponent(ref, Player.getComponentType());
            
            if (player == null) {
                return;
            }
            
            World world = player.getWorld();
            if (world == null) {
                return;
            }
            
            // Get the target block position
            Vector3i targetBlock = event.getTargetBlock();
            if (targetBlock == null) {
                return;
            }
            
            int x = targetBlock.getX();
            int y = targetBlock.getY();
            int z = targetBlock.getZ();
            
            // Check if target block itself is a shop
            BlockState targetState = world.getState(x, y, z, true);
            if (targetState instanceof BarterChestBlockState) {
                event.setCancelled(true);
                player.sendMessage(Message.raw("Shop chests cannot be broken! Use the shop menu to remove.").color(Color.RED));
                return;
            }
            
            // Check all 26 blocks in a 3x3x3 cube around the target
            if (isNearShop(world, x, y, z)) {
                event.setCancelled(true);
                player.sendMessage(Message.raw("Cannot break blocks near a shop chest!").color(Color.RED));
            }
        }
        
        /**
         * Check if a position is within a 3x3x3 cube of any shop.
         */
        private boolean isNearShop(World world, int x, int y, int z) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        
                        try {
                            BlockState state = world.getState(x + dx, y + dy, z + dz, true);
                            if (state instanceof BarterChestBlockState) {
                                return true;
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
            return false;
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
}
