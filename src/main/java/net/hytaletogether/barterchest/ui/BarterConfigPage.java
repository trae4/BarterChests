package net.hytaletogether.barterchest.ui;

import net.hytaletogether.barterchest.config.BarterConfig;
import net.hytaletogether.barterchest.integration.EconomyIntegration;
import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
import net.hytaletogether.barterchest.system.ShopLocationRegistry;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.ShopOwnershipRegistry;
import net.hytaletogether.barterchest.state.BarterListing;
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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
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
    private int quantityPerTransaction = 1;
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
                this.quantityPerTransaction = listing.getQuantityPerTransaction();
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
        
        // Digital Currency button - shows economy currency name if available
        if (EconomyIntegration.isAvailable()) {
            String digitalName = EconomyIntegration.getCurrencyNamePlural() + " (Wallet)";
            if (EconomyIntegration.isDigitalCurrency(selectedCurrencyId)) {
                commandBuilder.set("#DigitalCurrency.Text", "> " + digitalName + " <");
            } else {
                commandBuilder.set("#DigitalCurrency.Text", digitalName);
            }
        } else {
            commandBuilder.set("#DigitalCurrency.Text", "Digital (Unavailable)");
        }
        
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DigitalCurrency",
            EventData.of("Action", "digitalCurrency"));
        
        // Show prices
        commandBuilder.set("#BuyPriceLabel.Text", String.valueOf(buyPrice));
        commandBuilder.set("#SellPriceLabel.Text", String.valueOf(sellPrice));
        
        // Show quantity per transaction
        commandBuilder.set("#QtyLabel.Text", String.valueOf(quantityPerTransaction));
        // Price button events (explicit step buttons)
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyPlus",
            EventData.of("Action", "buyDelta:1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyPlus10",
            EventData.of("Action", "buyDelta:10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyPlus100",
            EventData.of("Action", "buyDelta:100"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyMinus",
            EventData.of("Action", "buyDelta:-1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyMinus10",
            EventData.of("Action", "buyDelta:-10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#BuyMinus100",
            EventData.of("Action", "buyDelta:-100"));

        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellPlus",
            EventData.of("Action", "sellDelta:1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellPlus10",
            EventData.of("Action", "sellDelta:10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellPlus100",
            EventData.of("Action", "sellDelta:100"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellMinus",
            EventData.of("Action", "sellDelta:-1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellMinus10",
            EventData.of("Action", "sellDelta:-10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SellMinus100",
            EventData.of("Action", "sellDelta:-100"));

        // Quantity button events (explicit step buttons)
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyPlus",
            EventData.of("Action", "qtyDelta:1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyPlus10",
            EventData.of("Action", "qtyDelta:10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyPlus100",
            EventData.of("Action", "qtyDelta:100"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyMinus",
            EventData.of("Action", "qtyDelta:-1"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyMinus10",
            EventData.of("Action", "qtyDelta:-10"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#QtyMinus100",
            EventData.of("Action", "qtyDelta:-100"));
        
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
        
        // Handle digital currency selection
        if ("digitalCurrency".equals(action)) {
            if (EconomyIntegration.isAvailable()) {
                selectedCurrencyId = EconomyIntegration.DIGITAL_CURRENCY_ID;
                lastMessage = "Currency: " + EconomyIntegration.getCurrencyNamePlural() + " (Wallet)";
            } else {
                lastMessage = "Digital currency is not available!";
            }
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }

        // Handle explicit step buttons for price/quantity
        if (action.startsWith("buyDelta:")) {
            try {
                int delta = Integer.parseInt(action.substring("buyDelta:".length()));
                buyPrice = Math.max(0, buyPrice + (delta * increment));
                lastMessage = "";
            } catch (NumberFormatException ignored) {
            }
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }

        if (action.startsWith("sellDelta:")) {
            try {
                int delta = Integer.parseInt(action.substring("sellDelta:".length()));
                sellPrice = Math.max(0, sellPrice + (delta * increment));
                lastMessage = "";
            } catch (NumberFormatException ignored) {
            }
            confirmRemove = false;
            rebuildAndUpdate(ref, store);
            return;
        }

        if (action.startsWith("qtyDelta:")) {
            try {
                int delta = Integer.parseInt(action.substring("qtyDelta:".length()));
                quantityPerTransaction = Math.max(1, Math.min(9999, quantityPerTransaction + delta));
                lastMessage = "";
            } catch (NumberFormatException ignored) {
            }
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
            if (buyPrice < 0 || sellPrice < 0) {
                lastMessage = "Prices cannot be negative!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            if (buyPrice > 1000000 || sellPrice > 1000000) {
                lastMessage = "Prices cannot exceed 1,000,000!";
                rebuildAndUpdate(ref, store);
                return;
            }

            if (buyPrice == 0 && sellPrice == 0) {
                lastMessage = "Set at least one price!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            // Validate quantity
            if (quantityPerTransaction <= 0) {
                lastMessage = "Quantity must be positive!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            if (quantityPerTransaction > 6400) { // 100 stacks
                lastMessage = "Quantity too large!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            // Validate currency
            if (selectedCurrencyId == null || selectedCurrencyId.isEmpty()) {
                lastMessage = "Invalid currency!";
                rebuildAndUpdate(ref, store);
                return;
            }
            
            // Save the configuration
            listing.setCurrencyItemId(selectedCurrencyId);
            listing.setBuyPrice(buyPrice);
            listing.setSellPrice(sellPrice);
            listing.setQuantityPerTransaction(quantityPerTransaction);
            shop.markNeedsSave();

            // Persist metadata so admin tooling can show up-to-date listing info
            ShopMetadataRegistry.updateListing(world.getName(), shopPosition.getX(), shopPosition.getY(), shopPosition.getZ(),
                    itemId, selectedCurrencyId, buyPrice, sellPrice, quantityPerTransaction);
            
            // Update the floating display
            shop.updateDisplay(world, shopPosition.getX(), shopPosition.getY(), shopPosition.getZ());
            
            // Send success message to player
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                String itemName = BarterConfig.getInstance().getCurrencyDisplayName(itemId);
                String currencyName = BarterConfig.getInstance().getCurrencyDisplayName(selectedCurrencyId);
                
                // Send colored messages using proper Hytale Message API
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Shop is now open for business!").color(java.awt.Color.GREEN).bold(true));
                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Selling: " + quantityPerTransaction + "x " + itemName + " per transaction").color(java.awt.Color.WHITE));
                
                if (buyPrice > 0) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Buy: " + quantityPerTransaction + "x for " + buyPrice + " " + currencyName).color(new java.awt.Color(100, 255, 100)));
                }
                if (sellPrice > 0) {
                    player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Sell: " + quantityPerTransaction + "x for " + sellPrice + " " + currencyName).color(new java.awt.Color(255, 180, 100)));
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
                net.hytaletogether.barterchest.display.BarterDisplayManager.removeDisplay(shop, world);
                
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

                    // Remove shop + neighbor protection from the fast index
                    // so blocks around the former shop can be edited again.
                    ProtectedShopIndex.get().removeShop(world, x, y, z);

                    // Remove from persistent registry so it will not be re-indexed on restart.
                    ShopLocationRegistry.unregister(world.getName(), x, y, z);

                    // Remove from metadata registry (admin tooling)
                    ShopMetadataRegistry.unregister(world.getName(), x, y, z);

                    // Update per-player shop count (for max shop limits)
                    ShopOwnershipRegistry.decrement(shop.getOwnerUUID());
                    
                    // Give the player back their Barter License
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        // Create a Barter License item to give back
                        ItemStack licenseItem = new ItemStack(
                            net.hytaletogether.barterchest.interaction.BarterLicenseInteraction.SHOP_LICENSE_ITEM_ID, 
                            1
                        );
                        
                        // Get the player's inventory container
                        Inventory playerInventory = player.getInventory();
                        boolean addedToInventory = false;
                        
                        if (playerInventory != null) {
                            ItemContainer playerContainer = playerInventory.getCombinedHotbarFirst();
                            if (playerContainer != null) {
                                // Use the transaction manager's helper to add the item
                                addedToInventory = net.hytaletogether.barterchest.transaction.BarterTransactionManager.addItemStack(
                                    playerContainer, licenseItem
                                );
                            }
                        }
                        
                        if (addedToInventory) {
                            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Shop removed! Barter License returned to your inventory.").color(java.awt.Color.GREEN));
                        } else {
                            // Inventory full - drop the license at the shop location
                            player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Shop removed! Barter License dropped (inventory full).").color(java.awt.Color.YELLOW));
                            
                            // Drop the item in the world
                            try {
                                com.hypixel.hytale.math.vector.Vector3d dropPos = new com.hypixel.hytale.math.vector.Vector3d(
                                    x + 0.5, y + 1.0, z + 0.5
                                );
                                com.hypixel.hytale.math.vector.Vector3f rotation = new com.hypixel.hytale.math.vector.Vector3f(0, 0, 0);
                                
                                // Get the entity store from the world to spawn the item drop
                                com.hypixel.hytale.server.core.universe.world.storage.EntityStore entityStore = 
                                    (com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData();
                                if (entityStore != null) {
                                    com.hypixel.hytale.server.core.modules.entity.item.ItemComponent.generateItemDrop(
                                        store, licenseItem, dropPos, rotation, 0.0f, 0.1f, 0.0f
                                    );
                                }
                            } catch (Exception dropError) {
                                // If dropping also fails, log it but don't block removal
                                player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Warning: Could not drop Barter License.").color(java.awt.Color.RED));
                            }
                        }
                        
                        player.sendMessage(com.hypixel.hytale.server.core.Message.raw("Items preserved in chest.").color(java.awt.Color.GREEN));
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
    
        private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
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
