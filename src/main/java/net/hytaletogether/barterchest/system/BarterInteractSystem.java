package net.hytaletogether.barterchest.system;

import net.hytaletogether.barterchest.admin.AdminModeManager;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.integration.SimpleClaimsIntegration;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import net.hytaletogether.barterchest.util.ShopPlateUtil;
import net.hytaletogether.barterchest.ui.BarterConfigPage;
import net.hytaletogether.barterchest.ui.BarterUIPage;
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
import com.hypixel.hytale.server.core.Message;
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
 * This system handles player interactions with BarterChests.
 * It operates at a very high priority to ensure it can intercept and handle
 * events before other plugins (like claim/protection plugins) have a chance to cancel them.
 * This is a pragmatic approach to avoid race conditions where, for example, a claim plugin
 * might prevent a player from opening a shop UI in a protected area.
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
        // IMPORTANT:
        // We intentionally keep this broad so we can un-cancel for BarterChests when needed,
        // but the handler itself MUST be extremely cheap for non-shop interactions.
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

        // Keep this handler very cheap for non-shop interactions:
        // - one state read (no chunk load)
        // - zero logging
        int bx = targetBlock.getX();
        int by = targetBlock.getY();
        int bz = targetBlock.getZ();

        // Resolve the "shop" coordinate.
        // Normally the player clicks the BarterChest block itself. But on servers with newer
        // SimpleClaims builds, public interaction can be blocked before we ever get a usable
        // chest interact. To keep shops usable, we also allow clicking a decorative plate
        // sitting directly *on top* of the shop chest.
        int shopX = bx;
        int shopY = by;
        int shopZ = bz;

        BlockState state = world.getState(bx, by, bz, false);
        BarterChestBlockState shop = (state instanceof BarterChestBlockState bcs) ? bcs : null;

        boolean plateClick = false;

        // If the clicked block isn't a BarterChest, check for a plate marker and resolve
        // the chest below.
        if (shop == null && ShopPlateUtil.isPlateAt(world, bx, by, bz)) {
            plateClick = true;
            shopY = by - 1;
            if (shopY < 0) return;

            BlockState below = world.getState(shopX, shopY, shopZ, false);
            if (below instanceof BarterChestBlockState bcsBelow) {
                shop = bcsBelow;
            } else {
                return;
            }
        }

        if (shop == null) return;

        Vector3i shopBlock = new Vector3i(shopX, shopY, shopZ);

        // Use the resolved shop coordinate for everything below.

        // Persist discovery so the protection index can be warmed on next restart,
        // without requiring any world scanning. Only add protection counts the first time
        // we discover a legacy shop (pre-registry) to avoid inflating ref-counts.
        boolean added = ShopLocationRegistry.ensureRegisteredAndReturnAdded(world.getName(), shopX, shopY, shopZ);
        if (added) {
            ProtectedShopIndex.get().addShop(world, shopX, shopY, shopZ);
        }
        
        // IMPORTANT: If this is a BarterChest, we handle it ourselves.
        // Un-cancel the event if it was cancelled by another system (like SimpleClaims)
        // because BarterChest shops are meant to be publicly accessible.
        if (event.isCancelled()) {
            event.setCancelled(false);
        }
        
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
        // (only makes sense when clicking the chest itself, not the plate marker)
        if (canManage && isCrouching && !plateClick) {
            // Don't cancel - let the default chest opening happen
            return;
        }
        
        // Cancel the default interaction
        event.setCancelled(true);
        
        // If manager (not crouching), open config UI
        if (canManage) {
            BarterConfigPage configPage = new BarterConfigPage(playerRef, shopBlock, world);
            player.getPageManager().openCustomPage(ref, store, configPage);
            return;
        }
        
        // For everyone else (customers, admins without admin mode): open shop UI
        BarterUIPage page = new BarterUIPage(playerRef, shopBlock, world);
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
