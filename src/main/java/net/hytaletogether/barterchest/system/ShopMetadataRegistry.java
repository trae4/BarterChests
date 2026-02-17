package net.hytaletogether.barterchest.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.Constants;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent registry of shop metadata for admin tooling.
 *
 * <p>Goals:
 * <ul>
 *   <li>Allow admin UIs to list shops without chunk loads</li>
 *   <li>Track owner + basic listing info (item + prices) as it changes</li>
 * </ul>
 */
public final class ShopMetadataRegistry {

    private static final Path DATA_DIR = Constants.UNIVERSE_PATH.resolve("BarterChest");
    private static final Path DATA_FILE = DATA_DIR.resolve("shop_metadata.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, ShopMetadata>>() {}.getType();

    private static final Object LOCK = new Object();
    private static Map<String, ShopMetadata> byKey = new HashMap<>();

    private ShopMetadataRegistry() {}

    public static void load() {
        synchronized (LOCK) {
            byKey.clear();
            if (!Files.exists(DATA_FILE)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(DATA_FILE)) {
                Map<String, ShopMetadata> raw = GSON.fromJson(reader, MAP_TYPE);
                if (raw != null) {
                    byKey = new HashMap<>(raw);
                }
            } catch (Exception ignored) {
                byKey = new HashMap<>();
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            try {
                Files.createDirectories(DATA_DIR);
                try (Writer writer = Files.newBufferedWriter(DATA_FILE)) {
                    GSON.toJson(byKey, writer);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void register(String worldName, int x, int y, int z, UUID ownerUuid, String ownerName) {
        if (worldName == null) return;
        String key = key(worldName, x, y, z);
        synchronized (LOCK) {
            ShopMetadata meta = byKey.get(key);
            if (meta == null) {
                meta = new ShopMetadata(worldName, x, y, z);
            }
            meta.ownerUuid = ownerUuid == null ? null : ownerUuid.toString();
            meta.ownerName = ownerName;
            byKey.put(key, meta);
            save();
        }
    }

    public static void updateListing(String worldName, int x, int y, int z,
                                     String itemId, String currencyItemId,
                                     int buyPrice, int sellPrice, int qtyPerTx) {
        if (worldName == null) return;
        String key = key(worldName, x, y, z);
        synchronized (LOCK) {
            ShopMetadata meta = byKey.get(key);
            if (meta == null) {
                meta = new ShopMetadata(worldName, x, y, z);
            }
            meta.itemId = itemId;
            meta.currencyItemId = currencyItemId;
            meta.buyPrice = buyPrice;
            meta.sellPrice = sellPrice;
            meta.quantityPerTransaction = qtyPerTx;
            byKey.put(key, meta);
            save();
        }
    }

    public static void unregister(String worldName, int x, int y, int z) {
        if (worldName == null) return;
        String key = key(worldName, x, y, z);
        synchronized (LOCK) {
            if (byKey.remove(key) != null) {
                save();
            }
        }
    }

    public static List<ShopMetadata> listAll() {
        synchronized (LOCK) {
            if (byKey.isEmpty()) return Collections.emptyList();
            List<ShopMetadata> out = new ArrayList<>(byKey.values());
            out.sort(Comparator
                .comparing((ShopMetadata m) -> Objects.toString(m.world, ""))
                .thenComparingInt(m -> m.x)
                .thenComparingInt(m -> m.z)
                .thenComparingInt(m -> m.y));
            return out;
        }
    }

    private static String key(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /** Serializable POJO (fields intentionally non-final for Gson). */
    public static final class ShopMetadata {
        public String world;
        public int x;
        public int y;
        public int z;

        public String ownerUuid;
        public String ownerName;

        public String itemId;
        public String currencyItemId;
        public int buyPrice;
        public int sellPrice;
        public int quantityPerTransaction;

        public ShopMetadata(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
