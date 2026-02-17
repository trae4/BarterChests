package net.hytaletogether.barterchest.ui;

import net.hytaletogether.barterchest.config.BarterConfig;
import net.hytaletogether.barterchest.state.BarterChestBlockState;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry;
import net.hytaletogether.barterchest.system.ShopMetadataRegistry.ShopMetadata;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.List;

/**
 * Admin-only shop list UI.
 *
 * Uses the same UI builder & event data patterns as BarterUIPage / BarterConfigPage,
 * so it compiles against the older server API.
 */
public class AdminShopListPage extends InteractiveCustomUIPage<AdminShopEventData> {

    private static final String UI_PAGE = "Pages/BarterChest_AdminShopListPage.ui";
    // The UI file defines ShopRow0..ShopRow19 (20 rows). Keep this in sync with the .ui document.
    private static final int MAX_ROWS = 20;

    // Pagination state (0-based page index).
    private int page = 0;

    public AdminShopListPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminShopEventData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder,
                      @Nonnull Store<EntityStore> store) {

        // Load the base UI
        commandBuilder.append(UI_PAGE);

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        // Permission gate (belt & suspenders; the command also requires this)
        if (!player.hasPermission(BarterChestBlockState.ADMIN_PERMISSION)) {
            playerRef.sendMessage(Message.raw("You do not have permission to view the admin shop list.")
                    .color(Color.RED));
            close();
            return;
        }

        List<ShopMetadata> shops = ShopMetadataRegistry.listAll();

        int total = shops.size();
        int maxPage = Math.max(0, (int) Math.ceil(total / (double) MAX_ROWS) - 1);
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        int startIndex = page * MAX_ROWS;
        int endIndex = Math.min(startIndex + MAX_ROWS, total);

        commandBuilder.set("#SummaryLabel.Text", "Shops tracked: " + total);
        commandBuilder.set("#PageLabel.Text", "Page " + (page + 1) + " / " + (maxPage + 1) + "  (" + (total == 0 ? 0 : (startIndex + 1)) + "-" + endIndex + ")");

        // Hide all rows by default.
        for (int i = 0; i < MAX_ROWS; i++) {
            commandBuilder.set("#ShopRow" + i + ".Visible", false);
        }

        int shown = Math.max(0, endIndex - startIndex);
        for (int i = 0; i < shown; i++) {
            ShopMetadata m = shops.get(startIndex + i);
            commandBuilder.set("#ShopRow" + i + ".Visible", true);

            String owner = (m.ownerName == null || m.ownerName.isBlank()) ? "Unknown" : m.ownerName;
            String item = (m.itemId == null || m.itemId.isBlank())
                    ? "(unconfigured)"
                    : formatItemNameSafe(m.itemId);

            String currency = (m.currencyItemId == null || m.currencyItemId.isBlank())
                    ? ""
                    : formatItemNameSafe(m.currencyItemId);

            String prices;
            if (m.buyPrice > 0 && m.sellPrice > 0) {
                prices = "Buy " + m.buyPrice + (currency.isEmpty() ? "" : (" " + currency)) + " / Sell " + m.sellPrice + (currency.isEmpty() ? "" : (" " + currency));
            } else if (m.buyPrice > 0) {
                prices = "Buy " + m.buyPrice + (currency.isEmpty() ? "" : (" " + currency));
            } else if (m.sellPrice > 0) {
                prices = "Sell " + m.sellPrice + (currency.isEmpty() ? "" : (" " + currency));
            } else {
                prices = "No prices set";
            }

            String qtyPrefix = (m.quantityPerTransaction > 0 ? (m.quantityPerTransaction + "x ") : "");
            commandBuilder.set("#LineA" + i + ".Text", owner + "  •  " + qtyPrefix + item);
            commandBuilder.set("#LineB" + i + ".Text", m.world + " @ " + m.x + "," + m.y + "," + m.z + "  •  " + prices);

            // Bind click → send Action string.
            // We keep the payload as a string to avoid relying on generics/Event objects.
            String action = "tp:" + m.world + ":" + m.x + ":" + m.y + ":" + m.z;
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ShopRow" + i,
                    EventData.of("Action", action));
        }

        // Page controls
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#PrevPageButton",
                EventData.of("Action", "page:prev"));
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating,
                "#NextPageButton",
                EventData.of("Action", "page:next"));

        // NOTE: We intentionally do NOT bind a "BackButton" here.
        // The shared $C.@BackButton template doesn't guarantee a fixed element id
        // across server/client builds. Binding to a missing selector will crash the client
        // with "Failed to apply CustomUI event bindings".
        // This page is CanDismiss, so players can close it normally.
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull AdminShopEventData data) {
        String action = data.getAction();
        if (action == null || action.isBlank()) {
            return;
        }

        if ("close".equalsIgnoreCase(action)) {
            close();
            return;
        }

        if ("page:next".equalsIgnoreCase(action)) {
            page++;
            rebuildAndUpdate(ref, store);
            return;
        }
        if ("page:prev".equalsIgnoreCase(action)) {
            page--;
            rebuildAndUpdate(ref, store);
            return;
        }

        if (!action.startsWith("tp:")) {
            return;
        }

        // tp:<world>:<x>:<y>:<z>
        String[] parts = action.split(":");
        if (parts.length != 5) {
            return;
        }
        String worldId = parts[1];
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
            z = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            return;
        }

        World world = Universe.get().getWorld(worldId);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (world == null) {
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("World '" + worldId + "' is not loaded.")
                        .color(Color.RED));
            }
            return;
        }

        // Teleport by writing a Teleport component.
        Vector3d pos = new Vector3d(x + 0.5, y + 1.0, z + 0.5);
        Vector3f rot = new Vector3f(0, 0, 0);
        Teleport tp = new Teleport(world, pos, rot);
        store.putComponent(ref, Teleport.getComponentType(), tp);

        close();
    }

    private void rebuildAndUpdate(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        build(ref, commandBuilder, eventBuilder, store);
        sendUpdate(commandBuilder, eventBuilder, true);
    }

    private static String formatItemNameSafe(String itemId) {
        // Reuse existing config formatting if present; otherwise fall back to the raw id.
        try {
            return BarterConfig.getInstance().getCurrencyDisplayName(itemId);
        } catch (Exception ignored) {
            return itemId;
        }
    }
}
