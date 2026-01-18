package com.example.barterchest.display;

import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.state.BarterListing;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages floating item displays above barter chests.
 * Creates a hovering item entity that shows what the shop sells.
 */
public class BarterDisplayManager {
    
    private static final HytaleLogger LOGGER = HytaleLogger.get("BarterChest/Display");
    
    /** Height offset above the shop chest for the floating item */
    private static final double DISPLAY_HEIGHT_OFFSET = 1.5;
    
    /** Very long lifetime for display items (24 hours in seconds) */
    private static final float DISPLAY_LIFETIME_SECONDS = 86400.0f;
    
    /**
     * Create or update the floating display for a shop.
     * Shows the first configured listing's item floating above the chest.
     */
    public static void createOrUpdateDisplay(
            @Nonnull BarterChestBlockState shop,
            @Nonnull World world,
            int blockX, int blockY, int blockZ
    ) {
        // Remove existing display first
        removeDisplayEntity(shop, world);
        
        // Find the first configured listing
        String displayItemId = null;
        
        for (BarterListing listing : shop.getListings()) {
            if (listing.isConfigured()) {
                displayItemId = listing.getItemId();
                break;
            }
        }
        
        // If no configured listing, try to get the first listing's item from chest
        if (displayItemId == null) {
            BarterListing listing = shop.getListing();
            if (listing != null && listing.getItemId() != null && !listing.getItemId().isEmpty()) {
                displayItemId = listing.getItemId();
            } else {
                // Fallback: get first item from chest
                ItemStack firstItem = shop.getItemAtSlot(0);
                if (firstItem != null && !ItemStack.isEmpty(firstItem)) {
                    displayItemId = firstItem.getItemId();
                }
            }
        }
        
        if (displayItemId == null || displayItemId.isEmpty()) {
            LOGGER.at(Level.FINE).log("No item to display for shop at %d, %d, %d", blockX, blockY, blockZ);
            return;
        }
        
        // Create display item stack from the item ID
        ItemStack displayItem = new ItemStack(displayItemId, 1);
        
        // Create position above the chest (centered)
        Vector3d displayPosition = new Vector3d(
            blockX + 0.5,
            blockY + DISPLAY_HEIGHT_OFFSET,
            blockZ + 0.5
        );
        
        // Create the display item entity
        try {
            Store<EntityStore> entityStore = world.getEntityStore().getStore();
            
            Holder<EntityStore> displayHolder = createDisplayItemHolder(
                entityStore,
                displayItem,
                displayPosition
            );
            
            if (displayHolder != null) {
                // Add the entity to the world
                entityStore.addEntities(new Holder[] { displayHolder }, AddReason.SPAWN);
                
                // Get the UUID and store it in the shop
                UUIDComponent uuidComponent = displayHolder.getComponent(UUIDComponent.getComponentType());
                if (uuidComponent != null) {
                    shop.setDisplayEntityUUID(uuidComponent.getUuid());
                    LOGGER.at(Level.FINE).log("Created display for shop at %d, %d, %d with item %s", 
                        blockX, blockY, blockZ, displayItemId);
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to create display for shop at %d, %d, %d: %s", 
                blockX, blockY, blockZ, e.getMessage());
        }
    }
    
    /**
     * Remove the floating display entity for a shop.
     */
    public static void removeDisplayEntity(@Nonnull BarterChestBlockState shop, @Nonnull World world) {
        UUID displayUUID = shop.getDisplayEntityUUID();
        if (displayUUID == null) {
            return;
        }
        
        try {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();
            
            Ref<EntityStore> entityRef = entityStore.getRefFromUUID(displayUUID);
            
            if (entityRef != null && entityRef.isValid()) {
                store.removeEntity(entityRef, RemoveReason.REMOVE);
                LOGGER.at(Level.FINE).log("Removed display entity %s", displayUUID);
            }
            
            shop.setDisplayEntityUUID(null);
            
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Failed to remove display entity: %s", e.getMessage());
            shop.setDisplayEntityUUID(null);
        }
    }
    
    /**
     * Remove the floating display for a shop (legacy method).
     */
    public static void removeDisplay(@Nonnull BarterChestBlockState shop, @Nonnull World world) {
        removeDisplayEntity(shop, world);
    }
    
    /**
     * Create a holder for a display item entity.
     */
    @Nullable
    private static Holder<EntityStore> createDisplayItemHolder(
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull ItemStack itemStack,
            @Nonnull Vector3d position
    ) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.isValid()) {
            return null;
        }
        
        ItemStack displayStack = new ItemStack(itemStack.getItemId(), 1);
        
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        
        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(displayStack));
        holder.addComponent(TransformComponent.getComponentType(), 
            new TransformComponent(position, Vector3f.ZERO));
        holder.ensureComponent(Intangible.getComponentType());
        holder.ensureComponent(PreventPickup.getComponentType());
        holder.ensureComponent(UUIDComponent.getComponentType());
        holder.ensureComponent(PhysicsValues.getComponentType());
        
        TimeResource timeResource = (TimeResource) accessor.getResource(TimeResource.getResourceType());
        if (timeResource != null) {
            holder.addComponent(DespawnComponent.getComponentType(), 
                DespawnComponent.despawnInSeconds(timeResource, DISPLAY_LIFETIME_SECONDS));
        }
        
        return holder;
    }
}
