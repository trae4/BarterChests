package net.hytaletogether.barterchest.util;

import net.hytaletogether.barterchest.BarterChestPlugin;
import net.hytaletogether.barterchest.config.BarterConfig;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.List;

/**
 * Places/removes a decorative "plate" block on top of a shop chest as an alternate interaction target.
 * <p>
 * Motivation: newer SimpleClaims versions can prevent non-members from interacting with protected
 * container blocks. Using a non-container "proxy" block gives us a second chance to open the shop UI.
 */
public final class ShopPlateUtil {

    // Note: The actual block ID is build-dependent, so we keep this configurable.
    // See BarterConfig.shopPlateBlockIds.

    private static final List<String> DEFAULT_PLATE_IDS = List.of(
            "Deco_Plate",
            "deco_plate",
            "deco_plate_01",
            "deco_plate_wood"
    );

    private static List<String> plateIds() {
        try {
            List<String> ids = BarterConfig.getInstance().getShopPlateBlockIds();
            if (ids != null && !ids.isEmpty()) {
                return ids;
            }
        } catch (Throwable ignored) {
            // fall back
        }
        return DEFAULT_PLATE_IDS;
    }

    private ShopPlateUtil() {
    }

    public static boolean isPlateBlockId(String blockTypeId) {
        if (blockTypeId == null) return false;
        String id = blockTypeId.toLowerCase();
        return id.contains("deco_plate") || id.equals("decoplate") || id.endsWith(":deco_plate");
    }

    public static boolean isPlateAt(World world, int x, int y, int z) {
        try {
            var type = world.getBlockType(x, y, z);
            return type != null && isPlateBlockId(type.getId());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isAirLike(String blockTypeId) {
        if (blockTypeId == null) return true;
        String s = blockTypeId.trim().toLowerCase();
        // Common ids across namespaces / revisions
        if (s.equals("air") || s.equals("empty") || s.equals("void") || s.equals("none")) return true;
        if (s.endsWith(":air") || s.endsWith(":empty") || s.endsWith(":void")) return true;
        // Some revisions use placeholder ids like "block.air"
        if (s.endsWith(".air") || s.endsWith(".empty")) return true;
        return false;
    }

    /** Convenience alias used from command/interaction code. */
    public static boolean placePlateAboveShop(World world, int x, int y, int z) {
        return tryPlacePlateAbove(world, x, y, z);
    }

    /** Convenience alias used from command/interaction code. */
    public static void removePlateAboveShop(World world, int x, int y, int z) {
        tryRemovePlateAbove(world, x, y, z);
    }

    public static boolean tryPlacePlateAbove(World world, int x, int y, int z) {
        if (world == null) return false;
        int px = x;
        int py = y + 1;
        int pz = z;

        // Don't overwrite non-air blocks if we can avoid it.
        try {
            var existing = world.getBlockType(px, py, pz);
            if (existing != null) {
                String existingId = existing.getId();
                if (!isAirLike(existingId) && !isPlateBlockId(existingId)) {
                    BarterChestPlugin.getPluginLogger()
                            .at(java.util.logging.Level.INFO)
                            .log("Shop plate NOT placed: block above shop is '{}' at {},{},{}", existingId, px, py, pz);
                    return false;
                }
            }
        } catch (Throwable t) {
            BarterChestPlugin.getPluginLogger()
                    .at(java.util.logging.Level.WARNING)
                    .withCause(t)
                    .log("Shop plate pre-check failed at {},{},{} (continuing anyway)", px, py, pz);
        }

        for (String id : plateIds()) {
            if (WorldBlockUtil.trySetBlockType(world, px, py, pz, id)) {
                BarterChestPlugin.getPluginLogger()
                        .at(java.util.logging.Level.INFO)
                        .log("Placed shop plate '{}' at {},{},{}", id, px, py, pz);
                return true;
            }
        }
        BarterChestPlugin.getPluginLogger()
                .at(java.util.logging.Level.WARNING)
                .log("Shop plate NOT placed: could not set any of {} at {},{},{}", plateIds(), px, py, pz);
        return false;
    }

    public static boolean tryRemovePlateAbove(World world, int x, int y, int z) {
        if (world == null) return false;
        int px = x;
        int py = y + 1;
        int pz = z;

        try {
            var existing = world.getBlockType(px, py, pz);
            if (existing == null || !isPlateBlockId(existing.getId())) {
                return false;
            }
        } catch (Throwable ignored) {
            // if we can't read, still attempt removal
        }

        return WorldBlockUtil.trySetBlockType(world, px, py, pz, "air");
    }
}
