package net.hytaletogether.barterchest.util;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.lang.reflect.Method;

/**
 * Tiny compatibility wrapper for setting blocks.
 *
 * Hytale has been moving some World/Chunk APIs around (and changing parameter types).
 * We avoid hard-compiling against specific setter signatures by using reflection.
 */
public final class WorldBlockUtil {

    private WorldBlockUtil() {}

    /**
     * Backwards-compatible alias used by some code paths.
     * (Historically this utility exposed different method names while we chased API changes.)
     */
    public static boolean trySetBlockType(World world, int x, int y, int z, String blockTypeId) {
        return trySetBlockTypeId(world, x, y, z, blockTypeId);
    }

    /**
     * Best-effort: set a block by *string* id (ex: "Deco_Plate" or "air").
     */
    public static boolean trySetBlockTypeId(World world, int x, int y, int z, String blockTypeId) {
        if (world == null || blockTypeId == null) return false;

        // 1) Try World setters directly.
        if (invokeFirstMatch(world,
                new Class<?>[]{int.class, int.class, int.class, String.class},
                new Object[]{x, y, z, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        // 2) Try to obtain a chunk and set on it.
        WorldChunk chunk = getChunkAtBlock(world, x, y, z);
        if (chunk == null) return false;

        // Many chunk APIs expect local coords, but in practice some also accept world coords.
        // We try world coords first, then local (x&15,z&15; plus a couple of common masks).
        if (invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, String.class},
                new Object[]{x, y, z, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        int lx16 = x & 15;
        int lz16 = z & 15;
        if (invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, String.class},
                new Object[]{lx16, y, lz16, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        int lx32 = x & 31;
        int lz32 = z & 31;
        return invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, String.class},
                new Object[]{lx32, y, lz32, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock");
    }

    /**
     * Best-effort: set a block by *numeric* id.
     */
    public static boolean trySetBlockTypeId(World world, int x, int y, int z, int blockTypeId) {
        if (world == null) return false;

        if (invokeFirstMatch(world,
                new Class<?>[]{int.class, int.class, int.class, int.class},
                new Object[]{x, y, z, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        WorldChunk chunk = getChunkAtBlock(world, x, y, z);
        if (chunk == null) return false;

        if (invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, int.class},
                new Object[]{x, y, z, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        int lx16 = x & 15;
        int lz16 = z & 15;
        if (invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, int.class},
                new Object[]{lx16, y, lz16, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock")) {
            return true;
        }

        int lx32 = x & 31;
        int lz32 = z & 31;
        return invokeFirstMatch(chunk,
                new Class<?>[]{int.class, int.class, int.class, int.class},
                new Object[]{lx32, y, lz32, blockTypeId},
                "setBlockTypeId", "setBlockType", "setBlock");
    }

    private static WorldChunk getChunkAtBlock(World world, int x, int y, int z) {
        // Newer API (we've seen both names in the wild): getChunkAtBlock / getChunkAt / getChunk
        Object chunk = invokeFirstMatchReturn(world,
                new Class<?>[]{int.class, int.class, int.class},
                new Object[]{x, y, z},
                "getChunkAtBlock", "getChunkAt", "getChunk");

        if (chunk instanceof WorldChunk) return (WorldChunk) chunk;
        return null;
    }

    private static boolean invokeFirstMatch(Object target, Class<?>[] paramTypes, Object[] args, String... names) {
        return invokeFirstMatchReturn(target, paramTypes, args, names) != null;
    }

    private static Object invokeFirstMatchReturn(Object target, Class<?>[] paramTypes, Object[] args, String... names) {
        Class<?> c = target.getClass();
        for (String name : names) {
            try {
                Method m = c.getMethod(name, paramTypes);
                m.setAccessible(true);
                return m.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (Throwable t) {
                // method exists but failed, try next candidate
            }
        }
        return null;
    }
}
