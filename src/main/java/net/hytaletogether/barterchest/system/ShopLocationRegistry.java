package net.hytaletogether.barterchest.system;

import net.hytaletogether.barterchest.protection.ProtectedShopIndex;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent registry of BarterChest shop locations.
 *
 * Why this exists:
 * - BarterChest shops are stored in the world as a custom BlockState, but at startup we
 *   need the neighbor protection index ready immediately to prevent block updates around
 *   shops from corrupting the custom inventory state.
 * - We cannot safely scan the entire world on startup.
 *
 * Design:
 * - Stores only explicit shop chest positions (worldName + x,y,z)
 * - On startup, loads and immediately warms {@link ProtectedShopIndex}
 * - Existing legacy shops that are not in this file will be discovered when interacted with
 *   (see BarterInteractSystem) and will then be persisted for next restart.
 */
public final class ShopLocationRegistry {

    private static final Path DATA_DIR = Constants.UNIVERSE_PATH.resolve("BarterChest");
    private static final Path DATA_FILE = DATA_DIR.resolve("shop_locations.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ShopLocation>>() {}.getType();

    private static final Object LOCK = new Object();

    // worldName -> packed pos set (center positions only)
    private static final Map<String, Set<Long>> SHOPS_BY_WORLD = new HashMap<>();

    private ShopLocationRegistry() {}

    public static void loadAndWarmProtectionIndex() {
        load();
        // Rebuild protection counts from scratch to ensure overlaps are reference-counted correctly
        // after restart (and to avoid stale entries if shops were removed while offline).
        ProtectedShopIndex.get().clearAll();
        // Warm the neighbor protection index immediately so block edits around known shops
        // are blocked even before anyone interacts with the shop after a restart.
        synchronized (LOCK) {
            for (Map.Entry<String, Set<Long>> e : SHOPS_BY_WORLD.entrySet()) {
                String worldName = e.getKey();
                for (long packed : e.getValue()) {
                    int x = unpackX(packed);
                    int y = unpackY(packed);
                    int z = unpackZ(packed);
                    ProtectedShopIndex.get().addShop(worldName, x, y, z);
                }
            }
        }
    }

    public static boolean isRegistered(String worldName, int x, int y, int z) {
        if (worldName == null) return false;
        synchronized (LOCK) {
            Set<Long> set = SHOPS_BY_WORLD.get(worldName);
            if (set == null || set.isEmpty()) return false;
            return set.contains(packBlockPos(x, y, z));
        }
    }

    /**
     * Backwards-compatible alias for {@link #ensureRegistered(String, int, int, int)}.
     */
    public static void registerIfMissing(String worldName, int x, int y, int z) {
        ensureRegistered(worldName, x, y, z);
    }

    /**
     * Register a shop location if missing and persist.
     *
     * <p><b>Important:</b> This registry is persistence-only. It must NOT mutate the live
     * {@link ProtectedShopIndex}. The protection index is updated by the shop create/remove
     * logic on the world thread.
     */
    public static void ensureRegistered(String worldName, int x, int y, int z) {
        if (worldName == null) return;
        long packed = packBlockPos(x, y, z);
        boolean added = false;
        synchronized (LOCK) {
            Set<Long> set = SHOPS_BY_WORLD.computeIfAbsent(worldName, k -> new HashSet<>());
            if (!set.contains(packed)) {
                set.add(packed);
                added = true;
            }
        }
        if (added) {
            save();
        }
    }

    /**
     * Same as {@link #ensureRegistered(String, int, int, int)} but returns whether an entry was newly added.
     * Useful for "discovering" legacy shops at runtime without double-counting protection.
     */
    public static boolean ensureRegisteredAndReturnAdded(String worldName, int x, int y, int z) {
        if (worldName == null) return false;
        long packed = packBlockPos(x, y, z);
        boolean added = false;
        synchronized (LOCK) {
            Set<Long> set = SHOPS_BY_WORLD.computeIfAbsent(worldName, k -> new HashSet<>());
            if (!set.contains(packed)) {
                set.add(packed);
                added = true;
            }
        }
        if (added) {
            save();
        }
        return added;
    }

    public static void unregister(String worldName, int x, int y, int z) {
        if (worldName == null) return;
        long packed = packBlockPos(x, y, z);
        boolean removed = false;
        synchronized (LOCK) {
            Set<Long> set = SHOPS_BY_WORLD.get(worldName);
            if (set != null) {
                removed = set.remove(packed);
                if (set.isEmpty()) {
                    SHOPS_BY_WORLD.remove(worldName);
                }
            }
        }
        if (removed) {
            save();
        }
    }

    /**
     * Returns a snapshot of all registered shop locations.
     * Key: World Name
     * Value: Set of packed block positions
     */
    public static Map<String, Set<Long>> getSnapshot() {
        synchronized (LOCK) {
            Map<String, Set<Long>> copy = new HashMap<>();
            for (Map.Entry<String, Set<Long>> entry : SHOPS_BY_WORLD.entrySet()) {
                copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
            return copy;
        }
    }

    public static int unpackX(long packed) {
        int x = (int) ((packed >> 38) & 0x3FFFFFFL);
        // sign extend 26-bit
        if ((x & 0x2000000) != 0) x |= ~0x3FFFFFF;
        return x;
    }

    public static int unpackZ(long packed) {
        int z = (int) ((packed >> 12) & 0x3FFFFFFL);
        if ((z & 0x2000000) != 0) z |= ~0x3FFFFFF;
        return z;
    }

    public static int unpackY(long packed) {
        return (int) (packed & 0xFFFL);
    }

    private static void load() {
        synchronized (LOCK) {
            SHOPS_BY_WORLD.clear();
            if (!Files.exists(DATA_FILE)) {
                // Create an empty file for transparency
                saveLocked(Collections.emptyList());
                return;
            }
            try (Reader reader = Files.newBufferedReader(DATA_FILE)) {
                List<ShopLocation> list = GSON.fromJson(reader, LIST_TYPE);
                if (list == null) return;
                for (ShopLocation loc : list) {
                    if (loc == null || loc.world == null) continue;
                    Set<Long> set = SHOPS_BY_WORLD.computeIfAbsent(loc.world, k -> new HashSet<>());
                    set.add(packBlockPos(loc.x, loc.y, loc.z));
                }
            } catch (Exception ignored) {
                // If parsing fails, keep empty rather than crashing startup
                SHOPS_BY_WORLD.clear();
            }
        }
    }

    public static void save() {
        synchronized (LOCK) {
            List<ShopLocation> out = new ArrayList<>();
            for (Map.Entry<String, Set<Long>> e : SHOPS_BY_WORLD.entrySet()) {
                String worldName = e.getKey();
                for (long packed : e.getValue()) {
                    out.add(new ShopLocation(worldName, unpackX(packed), unpackY(packed), unpackZ(packed)));
                }
            }
            saveLocked(out);
        }
    }

    private static void saveLocked(List<ShopLocation> out) {
        try {
            Files.createDirectories(DATA_DIR);
            try (Writer writer = Files.newBufferedWriter(DATA_FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private static long packBlockPos(int x, int y, int z) {
        long lx = ((long) x) & 0x3FFFFFFL;
        long lz = ((long) z) & 0x3FFFFFFL;
        long ly = ((long) y) & 0xFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }

    private static final class ShopLocation {
        final String world;
        final int x;
        final int y;
        final int z;

        ShopLocation(String world, int x, int y, int z) {
            this.world = Objects.requireNonNull(world);
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
