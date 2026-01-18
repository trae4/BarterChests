package com.example.barterchest.system;

import com.example.barterchest.admin.AdminModeManager;
import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.ui.BarterConfigPage;
import com.example.barterchest.ui.BarterUIPage;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * System that intercepts right-click interactions on shop blocks.
 * - Owner + crouching: opens chest inventory (for restocking)
 * - Owner (not crouching): opens config UI
 * - Admin (with admin mode ON) + crouching: opens chest inventory
 * - Admin (with admin mode ON, not crouching): opens config UI
 * - Admin (with admin mode OFF) or Customer: opens shop UI for buying/selling
 */
public class BarterInteractSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
    
    public BarterInteractSystem() {
        super(UseBlockEvent.Pre.class);
    }
    
    /**
     * Override to process cancelled events.
     * SimpleClaims may cancel the event before we see it, but we need to
     * un-cancel it for BarterChest shops so customers can access them.
     */
    @Override
    protected boolean shouldProcessEvent(@Nonnull UseBlockEvent.Pre event) {
        // Always process, even if cancelled - we'll check and un-cancel for BarterChests
        return true;
    }
    
    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                       @Nonnull UseBlockEvent.Pre event) {
        
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        
        if (player == null || playerRef == null) {
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
        
        // Check if the target block is a shop
        BlockState state = world.getState(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), true);
        
        // Debug: Log what type of block state we're seeing
        com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
            .log("Block at %d,%d,%d has state type: %s (event cancelled: %b)", 
                targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(),
                state != null ? state.getClass().getSimpleName() : "null",
                event.isCancelled());
        
        if (!(state instanceof BarterChestBlockState shop)) {
            return;
        }
        
        // IMPORTANT: If this is a BarterChest, we handle it ourselves.
        // Un-cancel the event if it was cancelled by another system (like SimpleClaims)
        // because BarterChest shops are meant to be publicly accessible.
        if (event.isCancelled()) {
            com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
                .log("Event was cancelled (likely by SimpleClaims) - allowing BarterChest interaction");
            event.setCancelled(false);
        }
        
        // Debug: Log interaction
        com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
            .log("Player %s interacting with shop at %d,%d,%d", 
                playerRef.getUsername(), targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        
        // Check if player is the owner
        UUID playerUUID = playerRef.getUuid();
        UUID ownerUUID = shop.getOwnerUUID();
        
        boolean isOwner = ownerUUID != null && ownerUUID.equals(playerUUID);
        boolean hasAdminPermission = player.hasPermission(BarterChestBlockState.ADMIN_PERMISSION);
        
        // Admin mode must be explicitly enabled for admins to manage other players' shops
        boolean isAdminManaging = hasAdminPermission && AdminModeManager.isAdminModeEnabled(playerUUID);
        
        // Can manage = owner OR admin with admin mode enabled
        boolean canManage = isOwner || isAdminManaging;
        
        // Debug: Log permission state
        com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
            .log("isOwner=%b, hasAdminPermission=%b, isAdminManaging=%b, canManage=%b", 
                isOwner, hasAdminPermission, isAdminManaging, canManage);
        
        // Check if player is crouching (sneaking)
        boolean isCrouching = false;
        MovementStatesComponent movementComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (movementComponent != null) {
            MovementStates movementStates = movementComponent.getMovementStates();
            if (movementStates != null) {
                isCrouching = movementStates.crouching;
            }
        }
        
        // If manager (owner or admin in admin mode) is crouching, let them access the chest inventory normally
        if (canManage && isCrouching) {
            // Don't cancel - let the default chest opening happen
            com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
                .log("Owner crouching - allowing chest access");
            return;
        }
        
        // Cancel the default interaction
        event.setCancelled(true);
        
        // If manager (not crouching), open config UI
        if (canManage) {
            com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
                .log("Opening config page for owner/admin");
            BarterConfigPage configPage = new BarterConfigPage(playerRef, targetBlock, world);
            player.getPageManager().openCustomPage(ref, store, configPage);
            return;
        }
        
        // For everyone else (customers, admins without admin mode): open shop UI
        com.hypixel.hytale.logger.HytaleLogger.get("BarterChest/Interact").at(java.util.logging.Level.INFO)
            .log("Opening shop UI for customer");
        BarterUIPage page = new BarterUIPage(playerRef, targetBlock, world);
        player.getPageManager().openCustomPage(ref, store, page);
    }
    
    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }
    
    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        // Use a very high priority (Integer.MIN_VALUE) to run as early as possible,
        // before SimpleClaims can cancel the event for BarterChest blocks
        return Collections.singleton(new RootDependency<>(Integer.MIN_VALUE));
    }
}
