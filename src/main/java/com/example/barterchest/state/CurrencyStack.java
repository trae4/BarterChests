package com.example.barterchest.state;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Represents a currency amount - an item ID and quantity.
 * Used to define prices for shop transactions.
 * 
 * Example: 5 gold coins = CurrencyStack("gold_coin", 5)
 */
public class CurrencyStack {
    
    @SuppressWarnings("unchecked")
    public static final Codec<CurrencyStack> CODEC = (Codec<CurrencyStack>)
        ((BuilderCodec.Builder<CurrencyStack>)
            ((BuilderCodec.Builder<CurrencyStack>)
                BuilderCodec.builder(CurrencyStack.class, CurrencyStack::new)
                    .addField(new KeyedCodec<>("ItemId", (Codec<String>) Codec.STRING),
                        (stack, id) -> stack.itemId = id,
                        stack -> stack.itemId)
            ).addField(new KeyedCodec<>("Quantity", (Codec<Integer>) Codec.INTEGER),
                (stack, qty) -> stack.quantity = qty,
                stack -> stack.quantity)
        ).build();
    
    private String itemId;
    private int quantity;
    
    // Default constructor for codec
    protected CurrencyStack() {
    }
    
    public CurrencyStack(@Nonnull String itemId, int quantity) {
        if (itemId == null || itemId.isEmpty()) {
            throw new IllegalArgumentException("Item ID cannot be null or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.itemId = itemId;
        this.quantity = quantity;
    }
    
    @Nonnull
    public String getItemId() {
        return itemId;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    /**
     * Creates a copy with a different quantity.
     */
    @Nonnull
    public CurrencyStack withQuantity(int newQuantity) {
        return new CurrencyStack(this.itemId, newQuantity);
    }
    
    /**
     * Calculates total cost for buying multiple units.
     */
    @Nonnull
    public CurrencyStack multiply(int multiplier) {
        return new CurrencyStack(this.itemId, this.quantity * multiplier);
    }
    
    @Override
    public String toString() {
        return quantity + "x " + itemId;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CurrencyStack that = (CurrencyStack) obj;
        return quantity == that.quantity && itemId.equals(that.itemId);
    }
    
    @Override
    public int hashCode() {
        return 31 * itemId.hashCode() + quantity;
    }
}
