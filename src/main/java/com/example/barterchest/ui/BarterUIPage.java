package com.example.barterchest.ui;

import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.state.BarterListing;
import com.example.barterchest.transaction.BarterTransactionManager;
import com.example.barterchest.transaction.TransactionResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Interactive shop UI page that allows players to buy/sell items.
 */
public class BarterUIPage extends InteractiveCustomUIPage<BarterEventData> {
    
    private static final String UI_PAGE = "Pages/BarterChest_ShopPage.ui";
    
    private final Vector3i shopPosition;
    private final World world;
    private String lastMessage = "";
    
    public BarterUIPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i shopPosition, @Nonnull World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, BarterEventData.CODEC);
        this.shopPosition = shopPosition;
        this.world = world;
    }
    
    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, 
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        
        // Load the base UI
        commandBuilder.append(UI_PAGE);
        
        BarterChestBlockState shop = getShop();
        if (shop == null) {
            commandBuilder.set("#TitleText.Text", "Shop Not Found");
            return;
        }
        
        // Set title
        String ownerName = shop.getOwnerName();
        commandBuilder.set("#TitleText.Text", ownerName + "'s Shop");
        
        BarterListing listing = shop.getListing();
        
        // Check if the listing is configured (has item ID and at least one price)
        boolean isConfigured = listing != null && 
                               listing.getItemId() != null && 
                               !listing.getItemId().isEmpty() &&
                               (listing.getBuyPrice() > 0 || listing.getSellPrice() > 0);
        
        if (!isConfigured) {
            commandBuilder.set("#ItemName.Text", "Not Configured");
            commandBuilder.set("#StockLabel.Text", "This shop has not been set up yet.");
            commandBuilder.set("#BuyPriceLabel.Text", "Not for sale");
            commandBuilder.set("#SellPriceLabel.Text", "Not buying");
            return;
        }
        
        // Get item info
        String itemId = listing.getItemId();
        int stock = BarterTransactionManager.countItems(shop.getItemContainer(), itemId);
        
        // Set item name (the #ItemName label, NOT the stock label)
        commandBuilder.set("#ItemName.Text", formatItemName(itemId));
        commandBuilder.set("#StockLabel.Text", "Item: " + formatItemName(itemId) + " | Stock: " + stock);
        
        // Get currency info
        String currencyId = listing.getCurrencyItemId();
        String currencyName = currencyId != null ? formatItemName(currencyId) : "None";
        
        // Set prices
        int buyPrice = listing.getBuyPrice();
        int sellPrice = listing.getSellPrice();
        boolean canBuy = listing.canBuyFrom() && stock > 0;
        boolean canSell = listing.canSellTo();
        
        if (buyPrice > 0) {
            commandBuilder.set("#BuyPriceLabel.Text", buyPrice + " " + currencyName);
        } else {
            commandBuilder.set("#BuyPriceLabel.Text", "Not for sale");
        }
        
        if (sellPrice > 0) {
            commandBuilder.set("#SellPriceLabel.Text", sellPrice + " " + currencyName);
        } else {
            commandBuilder.set("#SellPriceLabel.Text", "Not buying");
        }
        
        // Set message
        commandBuilder.set("#MessageLabel.Text", lastMessage);
        
        // Bind button events - quantity is encoded in action string (buy:1, sell:1)
        // Always bind if price is set - transaction manager will handle out of stock message
        if (buyPrice > 0) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyButton", 
                EventData.of("Action", "buy:1"));
        }
        
        if (canSell) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellButton", 
                EventData.of("Action", "sell:1"));
        }
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, 
                                @Nonnull BarterEventData data) {
        String actionRaw = data.getAction();
        
        if (actionRaw == null || actionRaw.isEmpty()) {
            return;
        }
        
        // Parse action and quantity from format "action:quantity" (e.g., "buy:1", "sell:10")
        String action = actionRaw;
        int quantity = 1;
        
        int colonIndex = actionRaw.indexOf(':');
        if (colonIndex > 0) {
            action = actionRaw.substring(0, colonIndex);
            try {
                quantity = Integer.parseInt(actionRaw.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                quantity = 1;
            }
        }
        
        // Apply shift modifier if held (10x quantity)
        if (data.isShiftHeld()) {
            quantity *= 10;
        }
        
        BarterChestBlockState shop = getShop();
        if (shop == null) {
            lastMessage = "Shop no longer exists!";
            rebuildAndUpdate(ref, store);
            return;
        }
        
        BarterListing listing = shop.getListing();
        if (listing == null) {
            lastMessage = "Shop not configured!";
            rebuildAndUpdate(ref, store);
            return;
        }
        
        // Get player inventory
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        
        Inventory inventory = playerComponent.getInventory();
        if (inventory == null) {
            lastMessage = "Error accessing inventory!";
            rebuildAndUpdate(ref, store);
            return;
        }
        
        ItemContainer playerInventory = inventory.getCombinedHotbarFirst();
        if (playerInventory == null) {
            lastMessage = "Error accessing inventory!";
            rebuildAndUpdate(ref, store);
            return;
        }
        
        TransactionResult result;
        
        if ("buy".equals(action)) {
            result = BarterTransactionManager.buyFromShop(shop, listing, playerInventory, quantity);
        } else if ("sell".equals(action)) {
            result = BarterTransactionManager.sellToShop(shop, listing, playerInventory, quantity);
        } else {
            return;
        }
        
        lastMessage = result.getMessage();
        rebuildAndUpdate(ref, store);
    }
    
    private void rebuildAndUpdate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        build(ref, commandBuilder, eventBuilder, store);
        sendUpdate(commandBuilder, eventBuilder, true);
    }
    
    private BarterChestBlockState getShop() {
        BlockState state = world.getState(shopPosition.getX(), shopPosition.getY(), shopPosition.getZ(), true);
        if (state instanceof BarterChestBlockState) {
            return (BarterChestBlockState) state;
        }
        return null;
    }
    
    private String formatItemName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "Unknown";
        }
        
        // Remove namespace (e.g., "hytale:oak_log" -> "oak_log")
        int colonIndex = itemId.lastIndexOf(':');
        String name = colonIndex >= 0 ? itemId.substring(colonIndex + 1) : itemId;
        
        // Replace underscores with spaces and capitalize each word
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        
        return result.toString();
    }
}
