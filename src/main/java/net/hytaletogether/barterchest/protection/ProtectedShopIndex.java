package net.hytaletogether.barterchest.protection;

import com.hypixel.hytale.server.core.universe.world.World;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast lookup index for blocks that must be protected to keep BarterChest shop blockstates stable.
 *
 * <p>Key goals:
 * <ul>
 *   <li>O(1) membership checks during high-frequency events (break/damage/place)</li>
 *   <li>No world reads (and especially no chunk loads) in the hot path</li>
 *   <li>Protected area = shop chest + all blocks in a 3x3x3 cube around it</li>
 * </ul>
 */
public final class ProtectedShopIndex {

    private static final ProtectedShopIndex INSTANCE = new ProtectedShopIndex();

    /**
     * Reference-counted protection map.
     *
     * <p>Why ref-counting? Protected areas can overlap (e.g. two shops one block apart share a 3x3 face).
     * If we used a plain Set and removed a shop's area, we'd accidentally unprotect blocks still required by
     * another shop.
     *
     * <p>Structure: worldName -> (packedBlockPos -> refCount)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> protectedCountsByWorld = new ConcurrentHashMap<>();

    private ProtectedShopIndex() {
    }

    public static ProtectedShopIndex get() {
        return INSTANCE;
    }

    public boolean isProtected(World world, int x, int y, int z) {
        if (world == null) return false;
        String worldName = safeWorldName(world);
        return isProtected(worldName, x, y, z);
    }

    public boolean isProtected(String worldName, int x, int y, int z) {
        if (worldName == null) return false;
        ConcurrentHashMap<Long, Integer> map = protectedCountsByWorld.get(worldName);
        if (map == null || map.isEmpty()) return false;
        return map.containsKey(packBlockPos(x, y, z));
    }

    /**
     * Adds the shop chest position and all neighbor blocks in a 3x3x3 cube (26 neighbors + center).
     */
    public void addShop(World world, int x, int y, int z) {
        if (world == null) return;
        String worldName = safeWorldName(world);
        addShop(worldName, x, y, z);
    }

    /**
     * Adds the shop chest position and neighbors using a raw world name.
     * Useful during startup reindexing when a World instance isn't available.
     */
    public void addShop(String worldName, int x, int y, int z) {
        if (worldName == null) return;
        ConcurrentHashMap<Long, Integer> map = protectedCountsByWorld.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final long key = packBlockPos(x + dx, y + dy, z + dz);
                    map.merge(key, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Removes the shop chest position and its 3x3x3 protected neighbor area.
     */
    public void removeShop(World world, int x, int y, int z) {
        if (world == null) return;
        String worldName = safeWorldName(world);
        removeShop(worldName, x, y, z);
    }

    /**
     * Removes the shop chest position and its protected neighbor area using a raw world name.
     */
    public void removeShop(String worldName, int x, int y, int z) {
        if (worldName == null) return;
        ConcurrentHashMap<Long, Integer> map = protectedCountsByWorld.get(worldName);
        if (map == null || map.isEmpty()) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final long key = packBlockPos(x + dx, y + dy, z + dz);
                    map.compute(key, (k, v) -> {
                        if (v == null) return null;
                        int next = v - 1;
                        return next <= 0 ? null : next;
                    });
                }
            }
        }
    }

    /**
     * Clears all protection counts. Intended for controlled rebuilds (e.g. on plugin startup)
     * where the caller will immediately re-add protection for all known shops.
     */
    public void clearAll() {
        protectedCountsByWorld.clear();
    }

    /**
     * Coordinate packing inspired by common voxel engines.
     *
     * <p>x and z stored in 26-bit signed two's complement (range ~ +/- 33M), y stored in 12 bits (0-4095).
     */
    private static long packBlockPos(int x, int y, int z) {
        long lx = ((long) x) & 0x3FFFFFFL;
        long lz = ((long) z) & 0x3FFFFFFL;
        long ly = ((long) y) & 0xFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }

    private static String safeWorldName(World world) {
        try {
            String n = world.getName();
            return n == null ? "<unknown>" : n;
        } catch (Throwable t) {
            return "<unknown>";
        }
    }
}
