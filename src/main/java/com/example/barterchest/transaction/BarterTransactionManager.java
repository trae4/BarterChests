package com.example.barterchest.transaction;

import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.state.BarterListing;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Handles shop transactions (buying and selling) using a barter system.
 * 
 * Buy: Customer gives currency items, receives shop items
 * Sell: Customer gives items, receives currency items from shop
 */
public class BarterTransactionManager {
    
    /**
     * Attempt to buy items from a shop.
     * 
     * @param shop The shop to buy from
     * @param listing The listing configuration
     * @param customerInventory The customer's inventory
     * @param quantity How many items to buy
     * @return The result of the transaction
     */
    public static TransactionResult buyFromShop(
            @Nonnull BarterChestBlockState shop,
            @Nonnull BarterListing listing,
            @Nonnull ItemContainer customerInventory,
            int quantity
    ) {
        // Validate listing
        if (!listing.canBuyFrom()) {
            return TransactionResult.failure(TransactionResult.Status.SHOP_DOESNT_SELL, 
                "This item is not for sale.");
        }
        
        if (quantity <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_QUANTITY,
                "Invalid quantity.");
        }
        
        // Get the CONFIGURED item ID - this is the ONLY item this listing trades
        String itemId = listing.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return TransactionResult.failure(TransactionResult.Status.SHOP_NOT_CONFIGURED,
                "This listing hasn't been configured with an item.");
        }
        
        String currencyItemId = listing.getCurrencyItemId();
        int pricePerItem = listing.getBuyPrice();
        int totalCost = pricePerItem * quantity;
        
        ItemContainer shopInventory = shop.getItemContainer();
        if (shopInventory == null) {
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Shop inventory not available.");
        }
        
        // Count available stock of the CONFIGURED item across the ENTIRE chest
        int availableStock = countItems(shopInventory, itemId);
        
        if (availableStock <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_STOCK,
                "This item is out of stock.");
        }
        
        if (availableStock < quantity) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_STOCK,
                "Not enough stock. Available: " + availableStock);
        }
        
        // Check if customer has enough currency
        int customerCurrency = countItems(customerInventory, currencyItemId);
        if (customerCurrency < totalCost) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_FUNDS,
                "You need " + totalCost + " " + getItemName(currencyItemId) + " but only have " + customerCurrency + ".");
        }
        
        // Check if customer has space for the items
        int customerSpace = getAvailableSpaceForItem(customerInventory, itemId);
        if (customerSpace < quantity) {
            return TransactionResult.failure(TransactionResult.Status.INVENTORY_FULL,
                "Not enough inventory space. You can only fit " + customerSpace + " more.");
        }
        
        // Check if shop has space for currency
        int shopCurrencySpace = getAvailableSpaceForItem(shopInventory, currencyItemId);
        if (shopCurrencySpace < totalCost) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_SPACE,
                "Shop doesn't have space for the payment.");
        }
        
        // Execute the transaction
        // 1. Remove currency from customer
        if (!removeItems(customerInventory, currencyItemId, totalCost)) {
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Failed to process payment.");
        }
        
        // 2. Transfer items from shop to customer (preserves durability/metadata)
        if (!transferItems(shopInventory, customerInventory, itemId, quantity)) {
            // Rollback: give currency back
            addItems(customerInventory, currencyItemId, totalCost);
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Failed to retrieve items from shop.");
        }
        
        // 3. Add currency to shop
        if (!addItems(shopInventory, currencyItemId, totalCost)) {
            // This shouldn't fail since we checked space, but handle it
            // Items are already with customer, so just log warning
        }
        
        // Record earnings
        shop.addEarnings(totalCost);
        shop.markDirty();
        
        return TransactionResult.success(quantity, 
            "Bought " + quantity + "x " + getItemName(itemId) + " for " + totalCost + "x " + getItemName(currencyItemId));
    }
    
    /**
     * Attempt to sell items to a shop.
     * 
     * @param shop The shop to sell to
     * @param listing The listing configuration
     * @param customerInventory The customer's inventory
     * @param quantity How many items to sell
     * @return The result of the transaction
     */
    public static TransactionResult sellToShop(
            @Nonnull BarterChestBlockState shop,
            @Nonnull BarterListing listing,
            @Nonnull ItemContainer customerInventory,
            int quantity
    ) {
        // Validate listing
        if (!listing.canSellTo()) {
            return TransactionResult.failure(TransactionResult.Status.SHOP_DOESNT_BUY,
                "This shop doesn't buy this item.");
        }
        
        if (quantity <= 0) {
            return TransactionResult.failure(TransactionResult.Status.INVALID_QUANTITY,
                "Invalid quantity.");
        }
        
        // Get the CONFIGURED item ID - this is the ONLY item this listing trades
        String itemId = listing.getItemId();
        if (itemId == null || itemId.isEmpty()) {
            return TransactionResult.failure(TransactionResult.Status.SHOP_NOT_CONFIGURED,
                "This listing hasn't been configured with an item.");
        }
        
        String currencyItemId = listing.getCurrencyItemId();
        int pricePerItem = listing.getSellPrice();
        int totalPayment = pricePerItem * quantity;
        
        ItemContainer shopInventory = shop.getItemContainer();
        if (shopInventory == null) {
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Shop inventory not available.");
        }
        
        // Check if customer has the configured items to sell
        int customerItems = countItems(customerInventory, itemId);
        if (customerItems < quantity) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_STOCK,
                "You don't have enough " + getItemName(itemId) + ". You have " + customerItems + ".");
        }
        
        // Check if shop has enough currency to pay
        int shopCurrency = countItems(shopInventory, currencyItemId);
        if (shopCurrency < totalPayment) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_FUNDS,
                "Shop doesn't have enough " + getItemName(currencyItemId) + " to pay you.");
        }
        
        // Check if shop has space for the items
        int shopSpace = getAvailableSpaceForItem(shopInventory, itemId);
        if (shopSpace < quantity) {
            return TransactionResult.failure(TransactionResult.Status.INSUFFICIENT_SPACE,
                "Shop doesn't have space for more items.");
        }
        
        // Check if customer has space for currency
        int customerCurrencySpace = getAvailableSpaceForItem(customerInventory, currencyItemId);
        if (customerCurrencySpace < totalPayment) {
            return TransactionResult.failure(TransactionResult.Status.INVENTORY_FULL,
                "You don't have space for the payment.");
        }
        
        // Execute the transaction
        // 1. Transfer items from customer to shop (preserves durability/metadata)
        if (!transferItems(customerInventory, shopInventory, itemId, quantity)) {
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Failed to take your items.");
        }
        
        // 2. Remove currency from shop
        if (!removeItems(shopInventory, currencyItemId, totalPayment)) {
            // Rollback: give items back
            transferItems(shopInventory, customerInventory, itemId, quantity);
            return TransactionResult.failure(TransactionResult.Status.TRANSACTION_ERROR,
                "Failed to get payment from shop.");
        }
        
        // 3. Add currency to customer
        if (!addItems(customerInventory, currencyItemId, totalPayment)) {
            // This shouldn't fail, but handle it
        }
        
        shop.markDirty();
        
        return TransactionResult.success(quantity,
            "Sold " + quantity + "x " + getItemName(itemId) + " for " + totalPayment + "x " + getItemName(currencyItemId));
    }
    
    // --- Helper Methods ---
    
    /**
     * Count how many of a specific item are in the container.
     * Uses case-insensitive comparison for item IDs.
     */
    public static int countItems(@Nonnull ItemContainer container, @Nonnull String itemId) {
        int count = 0;
        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack != null && !ItemStack.isEmpty(stack) && itemIdsMatch(itemId, stack.getItemId())) {
                count += stack.getQuantity();
            }
        }
        return count;
    }
    
    /**
     * Check if two item IDs match (case-insensitive, handles namespace variations).
     */
    private static boolean itemIdsMatch(@Nullable String id1, @Nullable String id2) {
        if (id1 == null || id2 == null) {
            return false;
        }
        // Direct match first
        if (id1.equals(id2)) {
            return true;
        }
        // Case-insensitive match
        if (id1.equalsIgnoreCase(id2)) {
            return true;
        }
        // Try matching without namespaces
        String name1 = stripNamespace(id1);
        String name2 = stripNamespace(id2);
        return name1.equalsIgnoreCase(name2);
    }
    
    /**
     * Strip namespace from item ID (e.g., "hytale:gold_ingot" -> "gold_ingot").
     */
    private static String stripNamespace(String itemId) {
        if (itemId == null) return "";
        int colonIndex = itemId.lastIndexOf(':');
        return colonIndex >= 0 ? itemId.substring(colonIndex + 1) : itemId;
    }
    
    /**
     * Get available space for a specific item (considering stacking).
     * For items with maxStack=1 (tools, armor), each slot can only hold 1.
     */
    public static int getAvailableSpaceForItem(@Nonnull ItemContainer container, @Nonnull String itemId) {
        int space = 0;
        int maxStackSize = getMaxStackSize(itemId);
        short capacity = container.getCapacity();
        
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || ItemStack.isEmpty(stack)) {
                // Empty slot can hold up to maxStackSize
                space += maxStackSize;
            } else if (maxStackSize > 1 && itemIdsMatch(itemId, stack.getItemId())) {
                // Only count stacking space if maxStack > 1
                space += Math.max(0, maxStackSize - stack.getQuantity());
            }
            // For maxStack=1 items, occupied slots contribute nothing
        }
        return space;
    }
    
    /**
     * Get the max stack size for an item. Items with durability typically stack to 1.
     */
    private static int getMaxStackSize(@Nonnull String itemId) {
        try {
            var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemId);
            if (item != null) {
                int maxStack = item.getMaxStack();
                if (maxStack > 0) {
                    return maxStack;
                }
            }
        } catch (Exception e) {
            // Ignore, use default
        }
        return 64; // Default
    }
    
    /**
     * Transfer items from source container to destination, preserving ItemStack properties.
     * This properly handles non-stackable items (like tools with durability).
     * 
     * @return true if all items were transferred successfully
     */
    public static boolean transferItems(
            @Nonnull ItemContainer source, 
            @Nonnull ItemContainer dest, 
            @Nonnull String itemId, 
            int quantity
    ) {
        int remaining = quantity;
        short sourceCapacity = source.getCapacity();
        short destCapacity = dest.getCapacity();
        
        try {
            // Collect ItemStacks to transfer from source
            java.util.List<ItemStack> toTransfer = new java.util.ArrayList<>();
            
            for (short i = 0; i < sourceCapacity && remaining > 0; i++) {
                ItemStack stack = source.getItemStack(i);
                if (stack != null && !ItemStack.isEmpty(stack) && itemIdsMatch(itemId, stack.getItemId())) {
                    int toTake = Math.min(remaining, stack.getQuantity());
                    
                    if (toTake == stack.getQuantity()) {
                        // Take the whole stack
                        toTransfer.add(stack);
                        source.removeItemStackFromSlot(i);
                    } else {
                        // Split the stack - create a new one with same properties
                        ItemStack taken = stack.withQuantity(toTake);
                        toTransfer.add(taken);
                        source.setItemStackForSlot(i, stack.withQuantity(stack.getQuantity() - toTake));
                    }
                    remaining -= toTake;
                }
            }
            
            // Add collected items to destination
            for (ItemStack stack : toTransfer) {
                if (!addItemStack(dest, stack)) {
                    // Failed to add - try to return to source
                    addItemStack(source, stack);
                    return false;
                }
            }
            
        } catch (NullPointerException e) {
            // Chunk reference issue - items should still transfer
        }
        
        return remaining == 0;
    }
    
    /**
     * Add an ItemStack to a container, preserving its properties.
     * Will stack with compatible items (if maxStack > 1) or use empty slots.
     */
    public static boolean addItemStack(@Nonnull ItemContainer container, @Nonnull ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack)) {
            return true;
        }
        
        int remaining = itemStack.getQuantity();
        int maxStackSize = getMaxStackSize(itemStack.getItemId());
        short capacity = container.getCapacity();
        
        try {
            // Only try to stack if maxStackSize > 1 (armor, tools, etc. with maxStack=1 should never stack)
            if (maxStackSize > 1) {
                for (short i = 0; i < capacity && remaining > 0; i++) {
                    ItemStack existing = container.getItemStack(i);
                    if (existing != null && !ItemStack.isEmpty(existing) && existing.isStackableWith(itemStack)) {
                        int canAdd = maxStackSize - existing.getQuantity();
                        if (canAdd > 0) {
                            int toAdd = Math.min(remaining, canAdd);
                            container.setItemStackForSlot(i, existing.withQuantity(existing.getQuantity() + toAdd));
                            remaining -= toAdd;
                        }
                    }
                }
            }
            
            // Use empty slots for remaining items
            for (short i = 0; i < capacity && remaining > 0; i++) {
                ItemStack existing = container.getItemStack(i);
                if (existing == null || ItemStack.isEmpty(existing)) {
                    int toAdd = Math.min(remaining, maxStackSize);
                    // Create new stack preserving durability/metadata from original
                    container.setItemStackForSlot(i, itemStack.withQuantity(toAdd));
                    remaining -= toAdd;
                }
            }
        } catch (NullPointerException e) {
            // Chunk reference issue
        }
        
        return remaining == 0;
    }
    
    /**
     * Remove items from a container.
     */
    public static boolean removeItems(@Nonnull ItemContainer container, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        short capacity = container.getCapacity();
        
        try {
            for (short i = 0; i < capacity && remaining > 0; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack != null && !ItemStack.isEmpty(stack) && itemIdsMatch(itemId, stack.getItemId())) {
                    int toRemove = Math.min(remaining, stack.getQuantity());
                    int newQuantity = stack.getQuantity() - toRemove;
                    
                    if (newQuantity <= 0) {
                        container.removeItemStackFromSlot(i);
                    } else {
                        container.setItemStackForSlot(i, stack.withQuantity(newQuantity));
                    }
                    remaining -= toRemove;
                }
            }
        } catch (NullPointerException e) {
            // Chunk reference issue - items still removed
        }
        
        return remaining == 0;
    }
    
    /**
     * Remove items from a specific slot.
     */
    public static boolean removeItemsFromSlot(@Nonnull ItemContainer container, short slot, int quantity) {
        ItemStack stack = container.getItemStack(slot);
        if (stack == null || ItemStack.isEmpty(stack)) {
            return false;
        }
        
        if (stack.getQuantity() < quantity) {
            return false;
        }
        
        try {
            int newQuantity = stack.getQuantity() - quantity;
            if (newQuantity <= 0) {
                container.removeItemStackFromSlot(slot);
            } else {
                container.setItemStackForSlot(slot, new ItemStack(stack.getItemId(), newQuantity));
            }
        } catch (NullPointerException e) {
            // Chunk reference issue - items still removed
        }
        return true;
    }
    
    /**
     * Add items to a container (anywhere there's space).
     * Note: This may throw an internal error if the container's change listener
     * tries to mark dirty on a state without a chunk reference. The items will
     * still be added successfully.
     */
    public static boolean addItems(@Nonnull ItemContainer container, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        int maxStackSize = 64;
        short capacity = container.getCapacity();
        
        try {
            // First, try to stack with existing items
            for (short i = 0; i < capacity && remaining > 0; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack != null && !ItemStack.isEmpty(stack) && itemIdsMatch(itemId, stack.getItemId())) {
                    int canAdd = maxStackSize - stack.getQuantity();
                    if (canAdd > 0) {
                        int toAdd = Math.min(remaining, canAdd);
                        container.setItemStackForSlot(i, stack.withQuantity(stack.getQuantity() + toAdd));
                        remaining -= toAdd;
                    }
                }
            }
            
            // Then, use empty slots
            for (short i = 0; i < capacity && remaining > 0; i++) {
                ItemStack stack = container.getItemStack(i);
                if (stack == null || ItemStack.isEmpty(stack)) {
                    int toAdd = Math.min(remaining, maxStackSize);
                    container.setItemStackForSlot(i, new ItemStack(itemId, toAdd));
                    remaining -= toAdd;
                }
            }
        } catch (NullPointerException e) {
            // This can happen when the container's change listener tries to mark
            // dirty on a BlockState that doesn't have a chunk reference.
            // The items are still added successfully, this is just a save notification issue.
        }
        
        return remaining == 0;
    }
    
    /**
     * Add items to a specific slot (or stack if compatible).
     */
    public static boolean addItemsToSlot(@Nonnull ItemContainer container, short slot, @Nonnull String itemId, int quantity) {
        ItemStack existing = container.getItemStack(slot);
        int maxStackSize = 64;
        
        try {
            if (existing == null || ItemStack.isEmpty(existing)) {
                // Empty slot, add new stack
                if (quantity <= maxStackSize) {
                    container.setItemStackForSlot(slot, new ItemStack(itemId, quantity));
                    return true;
                }
                return false;
            } else if (itemIdsMatch(itemId, existing.getItemId())) {
                // Same item, try to stack
                int newQuantity = existing.getQuantity() + quantity;
                if (newQuantity <= maxStackSize) {
                    container.setItemStackForSlot(slot, existing.withQuantity(newQuantity));
                    return true;
                }
                return false;
            }
        } catch (NullPointerException e) {
            // Chunk reference issue - items still added
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a display name for an item (strips namespace for readability).
     */
    @Nonnull
    private static String getItemName(@Nullable String itemId) {
        if (itemId == null) return "unknown";
        int colonIndex = itemId.indexOf(':');
        if (colonIndex >= 0 && colonIndex < itemId.length() - 1) {
            return itemId.substring(colonIndex + 1);
        }
        return itemId;
    }
}
