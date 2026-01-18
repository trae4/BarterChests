package com.example.barterchest.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.meta.state.BreakValidatedBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block state for player-owned shop chests.
 * Extends ItemContainerState to inherit chest inventory functionality.
 * Implements BreakValidatedBlockState to control who can break the shop.
 */
public class BarterChestBlockState extends ItemContainerState implements BreakValidatedBlockState {
    
    /** Permission node for admin bypass */
    public static final String ADMIN_PERMISSION = "barterchest.admin";
    
    /**
     * Full codec with all shop-specific fields.
     * IMPORTANT: Must include ItemContainer field for items to persist across restarts!
     */
    @SuppressWarnings("unchecked")
    public static final BuilderCodec<BarterChestBlockState> CODEC = 
        ((BuilderCodec.Builder<BarterChestBlockState>)
            ((BuilderCodec.Builder<BarterChestBlockState>)
                ((BuilderCodec.Builder<BarterChestBlockState>)
                    ((BuilderCodec.Builder<BarterChestBlockState>)
                        ((BuilderCodec.Builder<BarterChestBlockState>)
                            ((BuilderCodec.Builder<BarterChestBlockState>)
                                ((BuilderCodec.Builder<BarterChestBlockState>)
                                    ((BuilderCodec.Builder<BarterChestBlockState>)
                                        ((BuilderCodec.Builder<BarterChestBlockState>)
                                            BuilderCodec.builder(BarterChestBlockState.class, BarterChestBlockState::new)
                                                // Include Custom flag - when true, initialize() won't overwrite our container
                                                // Use reflection to avoid markNeedsSave() call during deserialization
                                                .addField(new KeyedCodec<>("Custom", (Codec<Boolean>) Codec.BOOLEAN),
                                                    (state, custom) -> {
                                                        try {
                                                            java.lang.reflect.Field f = ItemContainerState.class.getDeclaredField("custom");
                                                            f.setAccessible(true);
                                                            f.set(state, custom != null && custom);
                                                        } catch (Exception e) { /* ignore */ }
                                                    },
                                                    state -> true) // Always save as true since we have custom data
                                        ).addField(new KeyedCodec<>("ItemContainer", (Codec<SimpleItemContainer>) SimpleItemContainer.CODEC),
                                            (state, container) -> {
                                                if (container != null) {
                                                    try {
                                                        // Set directly via reflection to avoid markNeedsSave
                                                        java.lang.reflect.Field f = ItemContainerState.class.getDeclaredField("itemContainer");
                                                        f.setAccessible(true);
                                                        f.set(state, container);
                                                    } catch (Exception e) { /* ignore */ }
                                                }
                                            },
                                            state -> (SimpleItemContainer) state.getItemContainer())
                                    ).addField(new KeyedCodec<>("OwnerUUID", (Codec<UUID>) Codec.UUID_STRING),
                                        (state, uuid) -> state.ownerUUID = uuid,
                                        state -> state.ownerUUID)
                                ).addField(new KeyedCodec<>("OwnerName", (Codec<String>) Codec.STRING),
                                    (state, name) -> state.ownerName = name,
                                    state -> state.ownerName)
                            ).addField(new KeyedCodec<>("ShopName", (Codec<String>) Codec.STRING),
                                (state, name) -> state.shopName = name,
                                state -> state.shopName)
                        ).addField(new KeyedCodec<>("Listings", 
                                (Codec<BarterListing[]>) new ArrayCodec<>((Codec<BarterListing>) BarterListing.CODEC, BarterListing[]::new)),
                            (state, arr) -> { 
                                state.listings = new ArrayList<>();
                                if (arr != null) {
                                    for (BarterListing l : arr) state.listings.add(l);
                                }
                            },
                            state -> state.listings.toArray(new BarterListing[0]))
                    ).addField(new KeyedCodec<>("DisplayEntityUUID", (Codec<UUID>) Codec.UUID_STRING),
                        (state, uuid) -> state.displayEntityUUID = uuid,
                        state -> state.displayEntityUUID)
                ).addField(new KeyedCodec<>("CreatedAt", (Codec<Long>) Codec.LONG),
                    (state, instant) -> state.createdAt = instant,
                    state -> state.createdAt)
            ).addField(new KeyedCodec<>("TotalEarnings", (Codec<Long>) Codec.LONG),
                (state, earnings) -> state.totalEarnings = (earnings != null ? earnings : 0L),
                state -> state.totalEarnings)
        ).build();
    
    // --- Fields ---
    
    // Store as UUID directly since Codec.UUID_STRING handles String<->UUID conversion
    private UUID ownerUUID;
    private String ownerName;
    
    @Nullable
    private String shopName;
    
    @Nonnull
    private List<BarterListing> listings = new ArrayList<>();
    
    @Nullable
    private UUID displayEntityUUID;
    
    private long createdAt;
    private long totalEarnings = 0L;
    
    // --- Constructors ---
    
    // Default constructor for codec
    public BarterChestBlockState() {
        super();
    }
    
    /**
     * Initialize a new shop chest.
     */
    public static BarterChestBlockState create(@Nonnull UUID ownerUUID, @Nonnull String ownerName) {
        BarterChestBlockState state = new BarterChestBlockState();
        state.ownerUUID = ownerUUID;
        state.ownerName = ownerName;
        state.createdAt = Instant.now().toEpochMilli();
        return state;
    }
    
    // --- Permission Checks ---
    
    /**
     * Check if the given player UUID is the owner.
     */
    public boolean isOwner(@Nonnull UUID playerUUID) {
        return ownerUUID != null && ownerUUID.equals(playerUUID);
    }
    
    /**
     * Check if a player can modify this shop (owner or admin).
     */
    public boolean canModify(@Nonnull UUID playerUUID, boolean isAdmin) {
        return isOwner(playerUUID) || isAdmin;
    }
    
    /**
     * Override canOpen to allow interactions to proceed.
     * 
     * We return true for everyone so that the UseBlockEvent.Pre fires,
     * which our BarterInteractSystem will intercept to show the shop UI
     * for customers, or the config UI for owners.
     * 
     * The actual chest inventory access is controlled by BarterInteractSystem -
     * only owners who are crouching will see the chest inventory.
     */
    @Override
    public boolean canOpen(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // Always return true so the interaction proceeds and our event system can handle it
        return true;
    }
    
    /**
     * Implement BreakValidatedBlockState to restrict who can break the shop.
     * Shops cannot be broken directly - they must be removed via /barterchest remove
     * This ensures the floating display entity is properly cleaned up.
     */
    @Override
    public boolean canDestroy(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // Shops cannot be broken directly - must use /barterchest remove
        // This ensures proper cleanup of floating display entities
        return false;
    }
    
    // --- Listing Management ---
    
    /**
     * Get all listings.
     */
    @Nonnull
    public List<BarterListing> getListings() {
        return listings;
    }
    
    /**
     * Get the shop's listing (shops have a single listing at slot 0).
     */
    @Nullable
    public BarterListing getListing() {
        return getListing(0);
    }
    
    /**
     * Get a listing by slot, or null if not found.
     */
    @Nullable
    public BarterListing getListing(int slot) {
        for (BarterListing listing : listings) {
            if (listing.getSlot() == slot) {
                return listing;
            }
        }
        return null;
    }
    
    /**
     * Get or create a listing for a slot.
     */
    @Nonnull
    public BarterListing getOrCreateListing(int slot) {
        BarterListing existing = getListing(slot);
        if (existing != null) {
            return existing;
        }
        
        BarterListing newListing = new BarterListing();
        newListing.setSlot(slot);
        listings.add(newListing);
        markNeedsSave();
        return newListing;
    }
    
    /**
     * Remove a listing.
     */
    public void removeListing(int slot) {
        listings.removeIf(l -> l.getSlot() == slot);
        markNeedsSave();
    }
    
    // --- Stock Management ---
    
    /**
     * Get the current stock of the traded item for a specific listing.
     */
    public int getStock(BarterListing listing) {
        if (listing == null || listing.getItemId() == null) return 0;
        
        ItemContainer container = getItemContainer();
        int count = 0;
        short capacity = container.getCapacity();
        
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !ItemStack.isEmpty(stack) && stack.getItemId().equals(listing.getItemId())) {
                count += stack.getQuantity();
            }
        }
        
        return count;
    }
    
    /**
     * Get available space for buying items from customers.
     */
    public int getAvailableSpace(BarterListing listing) {
        if (listing == null || listing.getItemId() == null) return 0;
        
        ItemContainer container = getItemContainer();
        int space = 0;
        short capacity = container.getCapacity();
        
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || ItemStack.isEmpty(stack)) {
                space += 64; // Assume max stack size
            } else if (stack.getItemId().equals(listing.getItemId())) {
                space += Math.max(0, 64 - stack.getQuantity());
            }
        }
        
        return space;
    }
    
    // --- Getters ---
    
    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }
    
    @Nonnull
    public String getOwnerName() {
        return ownerName != null ? ownerName : "Unknown";
    }
    
    @Nullable
    public String getShopName() {
        return shopName;
    }
    
    /**
     * Returns the display name for the shop.
     */
    @Nonnull
    public String getDisplayName() {
        if (shopName != null && !shopName.isEmpty()) {
            return shopName;
        }
        return getOwnerName() + "'s Shop";
    }
    
    @Nullable
    public UUID getDisplayEntityUUID() {
        return displayEntityUUID;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getTotalEarnings() {
        return totalEarnings;
    }
    
    // --- Setters ---
    
    public void setShopName(@Nullable String shopName) {
        this.shopName = shopName;
        markNeedsSave();
    }
    
    public void setDisplayEntityUUID(@Nullable UUID displayEntityUUID) {
        this.displayEntityUUID = displayEntityUUID;
        markNeedsSave();
    }
    
    public void addEarnings(long amount) {
        this.totalEarnings += amount;
        markNeedsSave();
    }
    
    /**
     * Check if the shop has any configured listings.
     */
    public boolean isReady() {
        return !listings.isEmpty() && listings.stream().anyMatch(BarterListing::isConfigured);
    }
    
    /**
     * Get the item at a specific slot index.
     */
    @Nullable
    public ItemStack getItemAtSlot(int slotIndex) {
        ItemContainer container = getItemContainer();
        if (container == null || slotIndex < 0 || slotIndex >= container.getCapacity()) {
            return null;
        }
        return container.getItemStack((short) slotIndex);
    }
    
    /**
     * Alias for markNeedsSave for compatibility.
     */
    public void markDirty() {
        markNeedsSave();
    }
    
    /**
     * Override onDestroy to prevent crashes when the connected block system
     * corrupts our state. This can happen when explosions or other effects
     * trigger block updates near the shop.
     */
    @Override
    public void onDestroy() {
        try {
            // Check if chunk is still valid before calling parent
            if (getChunk() != null) {
                super.onDestroy();
            } else {
                // Chunk is null - state is corrupted, just clean up silently
                System.out.println("[BarterChest] Shop state destroyed with null chunk - skipping item drop");
            }
        } catch (Exception e) {
            // Catch any NPE and log it instead of crashing
            System.err.println("[BarterChest] Error during shop destroy: " + e.getMessage());
        }
    }
    
    /**
     * Override markNeedsSave to prevent crashes when chunk is null.
     */
    @Override
    public void markNeedsSave() {
        try {
            if (getChunk() != null) {
                super.markNeedsSave();
            }
        } catch (Exception e) {
            // Ignore - state may be in invalid state
        }
    }
    
    /**
     * Update the floating display entity above the shop.
     * Call this after changing the shop's item configuration.
     */
    public void updateDisplay(@Nonnull com.hypixel.hytale.server.core.universe.world.World world, int x, int y, int z) {
        com.example.barterchest.display.BarterDisplayManager.createOrUpdateDisplay(this, world, x, y, z);
    }
    
    @Override
    public String toString() {
        return "BarterChestBlockState{" +
                "owner=" + ownerName +
                ", shop=" + getDisplayName() +
                ", listings=" + listings.size() +
                "}";
    }
}
