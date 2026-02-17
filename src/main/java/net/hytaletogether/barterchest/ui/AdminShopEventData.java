package net.hytaletogether.barterchest.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data payload for the admin shop list UI.
 *
 * The UI sends a single string field named "Action".
 * Example values:
 *  - "close"
 *  - "tp:<world>:<x>:<y>:<z>"
 */
public class AdminShopEventData {

    public static final BuilderCodec<AdminShopEventData> CODEC = BuilderCodec
            .builder(AdminShopEventData.class, AdminShopEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (data, s) -> data.action = (s == null ? "" : s),
                    data -> data.action)
            .add()
            .build();

    private String action = "";

    public String getAction() {
        return action;
    }
}
