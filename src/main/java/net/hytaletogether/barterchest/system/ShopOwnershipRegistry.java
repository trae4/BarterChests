package net.hytaletogether.barterchest.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Constants;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent registry of how many BarterChests each player owns.
 *
 * This is used to enforce a max-shops-per-player limit.
 *
 * Notes:
 * - Counts are incremented when a shop is created via Barter License.
 * - Counts are decremented when a shop is removed via the config UI.
 * - Legacy shops created before this registry existed will not be counted
 *   until an admin runs a future rebuild tool.
 */
public final class ShopOwnershipRegistry {

    private static final Path DATA_DIR = Constants.UNIVERSE_PATH.resolve("BarterChest");
    private static final Path DATA_FILE = DATA_DIR.resolve("shop_ownership.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Object LOCK = new Object();
    private static Map<String, Integer> counts = new HashMap<>();

    private ShopOwnershipRegistry() {}

    public static void load() {
        synchronized (LOCK) {
            if (Files.exists(DATA_FILE)) {
                try (Reader reader = Files.newBufferedReader(DATA_FILE)) {
                    Map<String, Double> raw = GSON.fromJson(reader, Map.class);
                    counts = new HashMap<>();
                    if (raw != null) {
                        for (var e : raw.entrySet()) {
                            int v = e.getValue() == null ? 0 : (int) Math.round(e.getValue());
                            if (v > 0) counts.put(e.getKey(), v);
                        }
                    }
                } catch (Exception ignored) {
                    counts = new HashMap<>();
                }
            } else {
                counts = new HashMap<>();
                save();
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            try {
                Files.createDirectories(DATA_DIR);
                try (Writer writer = Files.newBufferedWriter(DATA_FILE)) {
                    GSON.toJson(counts, writer);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static int getCount(UUID owner) {
        if (owner == null) return 0;
        synchronized (LOCK) {
            return counts.getOrDefault(owner.toString(), 0);
        }
    }

    public static void increment(UUID owner) {
        if (owner == null) return;
        synchronized (LOCK) {
            String key = owner.toString();
            int next = counts.getOrDefault(key, 0) + 1;
            counts.put(key, next);
            save();
        }
    }

    public static void decrement(UUID owner) {
        if (owner == null) return;
        synchronized (LOCK) {
            String key = owner.toString();
            int cur = counts.getOrDefault(key, 0);
            int next = Math.max(0, cur - 1);
            if (next == 0) counts.remove(key);
            else counts.put(key, next);
            save();
        }
    }
}
