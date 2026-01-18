package com.example.barterchest.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data sent from client when interacting with shop UI.
 */
public class BarterEventData {
    
    public static final BuilderCodec<BarterEventData> CODEC = BuilderCodec.builder(BarterEventData.class, BarterEventData::new)
        .append(new KeyedCodec<>("Action", Codec.STRING), 
            (data, s) -> data.action = s, 
            data -> data.action)
        .add()
        .append(new KeyedCodec<>("Quantity", Codec.STRING), 
            (data, s) -> {
                try {
                    data.quantity = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    data.quantity = 1;
                }
            }, 
            data -> String.valueOf(data.quantity))
        .add()
        .append(new KeyedCodec<>("ShiftHeld", Codec.BOOLEAN), 
            (data, b) -> { if (b != null) data.shiftHeld = b; }, 
            data -> data.shiftHeld)
        .add()
        .build();
    
    private String action = "";
    private int quantity = 1;
    private boolean shiftHeld = false;
    
    public String getAction() {
        return action;
    }
    
    /**
     * Get quantity, with shift modifier (10x when shift held).
     */
    public int getQuantity() {
        return shiftHeld ? quantity * 10 : quantity;
    }
    
    public int getRawQuantity() {
        return quantity;
    }
    
    public boolean isShiftHeld() {
        return shiftHeld;
    }
}
