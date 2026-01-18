package com.example.barterchest.ui;

import com.example.barterchest.config.BarterConfig;
import com.example.barterchest.state.BarterChestBlockState;
import com.example.barterchest.state.BarterListing;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Configuration UI page for shop owners to set up their shops.
 */
public class BarterConfigPage extends InteractiveCustomUIPage<BarterConfigPage.ConfigData> {
    
    private static final String UI_PAGE = "Pages/BarterChest_ConfigPage.ui";
    
    private final Vector3i shopPosition;
    private final World world;
    
    // Edited values
    private String selectedCurrencyId;
    private int buyPrice = 0;
    private int sellPrice = 0;
    private String lastMessage = "";
    private boolean confirmRemove = false;
    
    public BarterConfigPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i shopPosition, @Nonnull World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, ConfigData.CODEC);
        this.shopPosition = shopPosition;
        this.world = world;
        
        // Load existing config
        BarterChestBlockState shop = getShop();
        if (shop != null) {
            BarterListing listing = shop.getListing();
            if (listing != null) {
                this.selectedCurrencyId = listing.getCurrencyItemId();
                this.buyPrice = listing.getBuyPrice();
                this.sellPrice = listing.getSellPrice();
            }
        }
        
        // Set default currency if not set
        if (selectedCurrencyId == null || selectedCurrencyId.isEmpty()) {
            selectedCurrencyId = BarterConfig.getInstance().getDefaultCurrency();
        }
    }
    
    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, 
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        
        commandBuilder.append(UI_PAGE);
        
        BarterChestBlockState shop = getShop();
        if (shop == null) {
            commandBuilder.set("#TitleText.Text", "Shop Not Found");
            commandBuilder.set("#ItemLabel.Text", "Error: Shop no longer exists");
            return;
        }
        
        // Title
        commandBuilder.set("#TitleText.Text", "Configure Your Shop");
        
        BarterListing listing = shop.getListing();
        
        // Show current item
        if (listing != null && listing.getItemId() != null && !listing.getItemId().isEmpty()) {
            commandBuilder.set("#ItemLabel.Text", "Selling: " + formatItemName(listing.getItemId()));
        } else {
            commandBuilder.set("#ItemLabel.Text", "Item: Not configured (add items to chest)");
        }
        
        // Set up currency buttons from config
        List<BarterConfig.CurrencyOption> currencies = BarterConfig.getInstance().getDefaultCurrencies();
        for (int i = 0; i < 4 && i < currencies.size(); i++) {
            BarterConfig.CurrencyOption currency = currencies.get(i);
            String buttonId = "#Currency" + i;
            
            // Highlight selected currency with marker in text
            if (currency.itemId.equals(selectedCurrencyId)) {
                commandBuilder.set(buttonId + ".Text", "> " + currency.displayName + " <");
            } else {
                commandBuilder.set(buttonId + ".Text", currency.displayName);
            }
            
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, buttonId,
                EventData.of("Action", "currency:" + i));
        }
        
        // Get item in player's hand for the "Use Item in Hand" button
        Player player = store.getComponent(ref, Player.getComponentType());
        String handItemId = null;
        if (player != null) {
            Inventory inv = player.getInventory();
            if (inv != null) {
                ItemStack handItem = inv.getItemInHand();
                if (handItem != null && !handItem.isEmpty()) {
                    handItemId = handItem.getItemId();
                }
            }
        }
        
        if (handItemId != null) {
            String displayName = BarterConfig.getInstance().getCurrencyDisplayName(handItemId);
            
            // Check if this hand item is the selected currency
            if (handItemId.equals(selectedCurrencyId)) {
                commandBuilder.set("#CurrencyFromHand.Text", "> " + displayName + " <");
            } else {
                commandBuilder.set("#CurrencyFromHand.Text", "Use: " + displayName);
            }
        } else {
            commandBuilder.set("#CurrencyFromHand.Text", "Hold item to use as currency");
        }
        
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CurrencyFromHand",
            EventData.of("Action", "currencyFromHand"));
        
        // Show prices
        commandBuilder.set("#BuyPriceLabel.Text", String.valueOf(buyPrice));
        commandBuilder.set("#SellPriceLabel.Text", String.valueOf(sellPrice));
        
        // Price button events
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyPlus",
            EventData.of("Action", "buyPlus"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyMinus",
            EventData.of("Action", "buyMinus"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellPlus",
            EventData.of("Action", "sellPlus"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellMinus",
            EventData.of("Action", "sellMinus"));
        
        // Message
        commandBuilder.set("#MessageLabel.Text", lastMessage);
        
        // Change remove button text if confirming
        if (confirmRemove) {
            commandBuilder.set("#RemoveShopButton.Text", "CONFIRM DELETE");
        } else {
            commandBuilder.set("#RemoveShopButton.Text", "Remove Shop");
        }
        
        // Bind main button events
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton",
            EventData.of("Action", "save"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#RemoveShopButton",
            EventData.of("Action", "remove"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"));
    }
    
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, 
                                @Nonnull ConfigData data) {
        
        String action = data.action;
        if (action == null || action.isEmpty()) {
            return;
        }
        
        BarterConfig config = BarterConfig.getInstance();
        
        // Determine increment based on modifiers
        // For now use base increment; shift/ctrl detection would need client-side support
        int increment = config.getPriceIncrement();
        
        // Handle currency selection
        if (action.startsWith("currency:")) {
            int index = Integer.parseInt(action.substring("currency:".length()));
            List<BarterConfig.CurrencyOption> currencies = config.getDefaultCurrencies();
            if (index >= 0 && index < currencies.size()) {
                selectedCurrencyId = currencies.get(index).itemId;
                lastMessage = "Currency: " + currencies.get(index).displayName;
            }
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        if ("currencyFromHand".equals(action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                Inventory inv = player.getInventory();
                if (inv != null) {
                    ItemStack handItem = inv.getItemInHand();
                    if (handItem != null && !handItem.isEmpty()) {
                        selectedCurrencyId = handItem.getItemId();
                        lastMessage = "Currency: " + config.getCurrencyDisplayName(selectedCurrencyId);
                    } else {
                        lastMessage = "Hold an item to use as currency!";
                    }
                }
            }
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        // Handle price adjustments
        if ("buyPlus".equals(action)) {
            buyPrice += increment;
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        if ("buyMinus".equals(action)) {
            buyPrice = Math.max(0, buyPrice - increment);
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        if ("sellPlus".equals(action)) {
            sellPrice += increment;
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        if ("sellMinus".equals(action)) {
            sellPrice = Math.max(0, sellPrice - increment);
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }
        
        if ("save".equals(action)) {
            BarterChestBlockState shop = getShop();
            if (shop == null) {
                lastMessage = "Shop no longer exists!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            BarterListing listing = shop.getOrCreateListing(0);
            
            // Auto-detect item from chest if not set
            String itemId = listing.getItemId();
            if (itemId == null || itemId.isEmpty()) {
                String detectedItem = detectItemFromChest(shop);
                if (detectedItem != null) {
                    itemId = detectedItem;
                    listing.setItemId(detectedItem);
                } else {
                    lastMessage = "Add items to chest first!";
                    rebuildAndUpdate(ref, store);
                    return;
                }
            }
            
            // Validate prices
            if (buyPrice <= 0 && sellPrice <= 0) {
                lastMessage = "Set at least one price!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            // Save the configuration
            listing.setCurrencyItemId(selectedCurrencyId);
            listing.setBuyPrice(buyPrice);
            listing.setSellPrice(sellPrice);
            shop.markNeedsSave();
            
            // Update the floating display
            shop.updateDisplay(world, shopPosition.getX(), shopPosition.getY(), shopPosition.getZ());
            
            // Send success message to player
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                String itemName = BarterConfig.getInstance().getCurrencyDisplayName(itemId);
                String currencyName = BarterConfig.getInstance().getCurrencyDisplayName(selectedCurrencyId);
                
                // Send colored messages using proper Hytale Message API
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Shop is now open for business!").color(java.awt.Color.GREEN).bold(true));
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Selling: " + itemName).color(java.awt.Color.WHITE));
                
                if (buyPrice > 0) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Buy price: " + buyPrice + " " + currencyName).color(new java.awt.Color(100, 255, 100)));
                }
                if (sellPrice > 0) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Sell price: " + sellPrice + " " + currencyName).color(new java.awt.Color(255, 180, 100)));
                }
            }
            
            // Close the GUI
            close();
            return;
        }
        
        if ("remove".equals(action)) {
            if (!confirmRemove) {
                confirmRemove = true;
                lastMessage = "Click again to confirm!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            // Actually remove the shop
            BarterChestBlockState shop = getShop();
            if (shop != null) {
                int x = shopPosition.getX();
                int y = shopPosition.getY();
                int z = shopPosition.getZ();
                
                // First, remove the floating display entity
                com.example.barterchest.display.BarterDisplayManager.removeDisplay(shop, world);
                
                // Get chunk
                WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk != null) {
                    // Get the block type to properly initialize new state
                    com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockType = chunk.getBlockType(x, y, z);
                    
                    // Get the container from the shop BEFORE replacing the state
                    com.hypixel.hytale.server.core.inventory.container.ItemContainer shopContainer = shop.getItemContainer();
                    com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer containerToTransfer = null;
                    
                    if (shopContainer instanceof com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer simpleContainer) {
                        containerToTransfer = simpleContainer;
                        
                        // Replace the shop's container with an EMPTY container using reflection
                        // This prevents onDestroy() from dropping the items when we replace the state
                        try {
                            java.lang.reflect.Field containerField = com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState.class.getDeclaredField("itemContainer");
                            containerField.setAccessible(true);
                            containerField.set(shop, new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
                        } catch (Exception e) {
                            lastMessage = "Error: " + e.getMessage();
                            rebuildAndUpdate(ref, store);
                            return;
                        }
                    }
                    
                    // Create a new regular ItemContainerState
                    com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState newState = 
                        new com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState();
                    
                    // Use reflection to set fields directly
                    try {
                        // Set custom = true so initialize() won't create a new container
                        java.lang.reflect.Field customField = com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState.class.getDeclaredField("custom");
                        customField.setAccessible(true);
                        customField.set(newState, true);
                        
                        // Set the container directly
                        if (containerToTransfer != null) {
                            java.lang.reflect.Field containerField = com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState.class.getDeclaredField("itemContainer");
                            containerField.setAccessible(true);
                            containerField.set(newState, containerToTransfer);
                        }
                    } catch (Exception e) {
                        lastMessage = "Error transferring items: " + e.getMessage();
                        rebuildAndUpdate(ref, store);
                        return;
                    }
                    
                    // Now initialize (custom=true means it won't override our container)
                    newState.initialize(blockType);
                    
                    // Replace shop state with regular container state
                    chunk.setState(x, y, z, newState);
                    
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Shop removed! Items preserved in chest.").color(java.awt.Color.GREEN));
                    }
                }
            }
            
            close();
            return;
        }
        
        if ("close".equals(action)) {
            close();
            return;
        }
        
        confirmRemove = false;
    }
    
    private String detectItemFromChest(BarterChestBlockState shop) {
        var container = shop.getItemContainer();
        if (container != null) {
            for (int i = 0; i < container.getCapacity(); i++) {
                ItemStack stack = container.getItemStack((short) i);
                if (stack != null && !stack.isEmpty()) {
                    return stack.getItemId();
                }
            }
        }
        return null;
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
        return BarterConfig.getInstance().getCurrencyDisplayName(itemId);
    }
    
    /**
     * Event data codec for config page
     */
    public static class ConfigData {
        public static final BuilderCodec<ConfigData> CODEC = BuilderCodec.<ConfigData>builder(ConfigData.class, ConfigData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action)
                .build();
        
        private String action;
        
        public String getAction() { return action; }
    }
}
