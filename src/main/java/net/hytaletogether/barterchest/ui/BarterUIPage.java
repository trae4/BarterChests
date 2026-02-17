package net.hytaletogether.barterchest.ui;

import net.hytaletogether.barterchest.integration.EconomyIntegration;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.state.BarterListing;
import net.hytaletogether.barterchest.transaction.BarterTransactionManager;
import net.hytaletogether.barterchest.transaction.TransactionResult;
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
import java.util.UUID;

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
        int qtyPerTx = listing.getQuantityPerTransaction();
        
        // Set item name (the #ItemName label, NOT the stock label)
        commandBuilder.set("#ItemName.Text", formatItemName(itemId));
        commandBuilder.set("#StockLabel.Text", "Item: " + formatItemName(itemId) + " | Stock: " + stock);
        
        // Get currency info
        String currencyId = listing.getCurrencyItemId();
        String currencyName;
        if (EconomyIntegration.isDigitalCurrency(currencyId)) {
            currencyName = EconomyIntegration.getCurrencyNamePlural();
        } else {
            currencyName = currencyId != null ? formatItemName(currencyId) : "None";
        }
        
        // Set prices (showing quantity per transaction)
        int buyPrice = listing.getBuyPrice();
        int sellPrice = listing.getSellPrice();
        boolean canBuy = listing.canBuyFrom() && stock > 0;
        boolean canSell = listing.canSellTo();
        
        if (buyPrice > 0) {
            commandBuilder.set("#BuyPriceLabel.Text", "Buy " + qtyPerTx + "x for " + buyPrice + " " + currencyName);
        } else {
            commandBuilder.set("#BuyPriceLabel.Text", "Not for sale");
        }
        
        if (sellPrice > 0) {
            commandBuilder.set("#SellPriceLabel.Text", "Sell " + qtyPerTx + "x for " + sellPrice + " " + currencyName);
        } else {
            commandBuilder.set("#SellPriceLabel.Text", "Not buying");
        }
        
        // Set button text to show quantities
        if (buyPrice > 0) {
            commandBuilder.set("#BuyButton1.Text", "Buy " + qtyPerTx);
            commandBuilder.set("#BuyButton10.Text", "Buy " + (qtyPerTx * 10));
            commandBuilder.set("#BuyButton100.Text", "Buy " + (qtyPerTx * 100));
        }
        if (sellPrice > 0) {
            commandBuilder.set("#SellButton1.Text", "Sell " + qtyPerTx);
            commandBuilder.set("#SellButton10.Text", "Sell " + (qtyPerTx * 10));
            commandBuilder.set("#SellButton100.Text", "Sell " + (qtyPerTx * 100));
        }
        
        // Set message
        commandBuilder.set("#MessageLabel.Text", lastMessage);
        
        // Bind button events - quantity from listing is used in transaction
        // Always bind if price is set - transaction manager will handle out of stock message
        if (buyPrice > 0) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyButton1",
                EventData.of("Action", "buy:" + qtyPerTx));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyButton10",
                EventData.of("Action", "buy:" + (qtyPerTx * 10)));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyButton100",
                EventData.of("Action", "buy:" + (qtyPerTx * 100)));
        }

        if (canSell) {
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellButton1",
                EventData.of("Action", "sell:" + qtyPerTx));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellButton10",
                EventData.of("Action", "sell:" + (qtyPerTx * 10)));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellButton100",
                EventData.of("Action", "sell:" + (qtyPerTx * 100)));
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
        
        // Get customer UUID for digital currency transactions
        UUID customerUUID = playerRef.getUuid();
        
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
            result = BarterTransactionManager.buyFromShop(shop, listing, playerInventory, customerUUID, quantity);
        } else if ("sell".equals(action)) {
            result = BarterTransactionManager.sellToShop(shop, listing, playerInventory, customerUUID, quantity);
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
