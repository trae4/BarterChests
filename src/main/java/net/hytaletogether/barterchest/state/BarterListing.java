package net.hytaletogether.barterchest.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a shop's listing configuration for a specific slot.
 * 
 * Supports a barter system where the shop owner specifies:
 * - The item being sold (from the chest slot)
 * - The currency item (any item ID)
 * - Buy price: how many currency items customers pay to buy items
 * - Sell price: how many currency items the shop pays when customers sell items
 * - Quantity per transaction: how many items are bought/sold per transaction
 */
public class BarterListing {
    
    @SuppressWarnings("unchecked")
    public static final Codec<BarterListing> CODEC = (Codec<BarterListing>)
        ((BuilderCodec.Builder<BarterListing>)
            ((BuilderCodec.Builder<BarterListing>)
                ((BuilderCodec.Builder<BarterListing>)
                    ((BuilderCodec.Builder<BarterListing>)
                        ((BuilderCodec.Builder<BarterListing>)
                            ((BuilderCodec.Builder<BarterListing>)
                                BuilderCodec.builder(BarterListing.class, BarterListing::new)
                                    .addField(new KeyedCodec<>("Slot", (Codec<Integer>) Codec.INTEGER),
                                        (listing, slot) -> listing.slot = slot,
                                        listing -> listing.slot)
                            ).addField(new KeyedCodec<>("ItemId", (Codec<String>) Codec.STRING),
                                (listing, id) -> listing.itemId = id,
                                listing -> listing.itemId)
                        ).addField(new KeyedCodec<>("CurrencyItemId", (Codec<String>) Codec.STRING),
                            (listing, id) -> listing.currencyItemId = id,
                            listing -> listing.currencyItemId)
                    ).addField(new KeyedCodec<>("BuyPrice", (Codec<Integer>) Codec.INTEGER),
                        (listing, price) -> listing.buyPrice = (price != null ? price : 0),
                        listing -> listing.buyPrice)
                ).addField(new KeyedCodec<>("SellPrice", (Codec<Integer>) Codec.INTEGER),
                    (listing, price) -> listing.sellPrice = (price != null ? price : 0),
                    listing -> listing.sellPrice)
            ).addField(new KeyedCodec<>("QuantityPerTransaction", (Codec<Integer>) Codec.INTEGER),
                (listing, qty) -> listing.quantityPerTransaction = (qty != null && qty > 0 ? qty : 1),
                listing -> listing.quantityPerTransaction)
        ).build();
    
    private int slot = 0;
    
    /** The item ID being sold (can be auto-detected from chest slot) */
    @Nullable
    private String itemId;
    
    /** The currency item ID that customers must pay with */
    @Nullable
    private String currencyItemId;
    
    /** Price (in currency items) customers pay to buy items from shop */
    private int buyPrice = 0;
    
    /** Price (in currency items) shop pays when customers sell items to it */
    private int sellPrice = 0;
    
    /** How many items are traded per transaction (default 1) */
    private int quantityPerTransaction = 1;
    
    public BarterListing() {
    }
    
    public BarterListing(int slot) {
        this.slot = slot;
    }
    
    public BarterListing(int slot, @Nullable String itemId, @Nullable String currencyItemId, int buyPrice, int sellPrice) {
        this.slot = slot;
        this.itemId = itemId;
        this.currencyItemId = currencyItemId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantityPerTransaction = 1;
    }
    
    public BarterListing(int slot, @Nullable String itemId, @Nullable String currencyItemId, int buyPrice, int sellPrice, int quantityPerTransaction) {
        this.slot = slot;
        this.itemId = itemId;
        this.currencyItemId = currencyItemId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.quantityPerTransaction = Math.max(1, quantityPerTransaction);
    }
    
    // --- Getters and Setters ---
    
    public int getSlot() {
        return slot;
    }
    
    public void setSlot(int slot) {
        this.slot = slot;
    }
    
    @Nullable
    public String getItemId() {
        return itemId;
    }
    
    public void setItemId(@Nullable String itemId) {
        this.itemId = itemId;
    }
    
    @Nullable
    public String getCurrencyItemId() {
        return currencyItemId;
    }
    
    public void setCurrencyItemId(@Nullable String currencyItemId) {
        this.currencyItemId = currencyItemId;
    }
    
    public int getBuyPrice() {
        return buyPrice;
    }
    
    public void setBuyPrice(int buyPrice) {
        this.buyPrice = buyPrice;
    }
    
    public int getSellPrice() {
        return sellPrice;
    }
    
    public void setSellPrice(int sellPrice) {
        this.sellPrice = sellPrice;
    }
    
    public int getQuantityPerTransaction() {
        return Math.max(1, quantityPerTransaction);
    }
    
    public void setQuantityPerTransaction(int quantityPerTransaction) {
        this.quantityPerTransaction = Math.max(1, quantityPerTransaction);
    }
    
    // --- Helper Methods ---
    
    /**
     * Returns true if customers can buy this item from the shop.
     */
    public boolean canBuyFrom() {
        return buyPrice > 0 && currencyItemId != null && !currencyItemId.isEmpty();
    }
    
    /**
     * Returns true if customers can sell this item to the shop.
     */
    public boolean canSellTo() {
        return sellPrice > 0 && currencyItemId != null && !currencyItemId.isEmpty();
    }
    
    /**
     * Returns true if this listing is fully configured (has item, currency, and at least one price).
     */
    public boolean isConfigured() {
        return itemId != null && !itemId.isEmpty() &&
               currencyItemId != null && !currencyItemId.isEmpty() &&
               (buyPrice > 0 || sellPrice > 0);
    }
    
    /**
     * Returns true if the currency is fully configured.
     */
    public boolean hasCurrency() {
        return currencyItemId != null && !currencyItemId.isEmpty();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BarterListing{slot=").append(slot);
        if (itemId != null) {
            sb.append(", item=").append(itemId);
        }
        if (currencyItemId != null) {
            sb.append(", currency=").append(currencyItemId);
        }
        if (buyPrice > 0) {
            sb.append(", buy=").append(buyPrice);
        }
        if (sellPrice > 0) {
            sb.append(", sell=").append(sellPrice);
        }
        sb.append(", qty=").append(quantityPerTransaction);
        sb.append("}");
        return sb.toString();
    }
}
