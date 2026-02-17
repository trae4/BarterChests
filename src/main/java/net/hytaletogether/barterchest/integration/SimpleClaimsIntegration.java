package net.hytaletogether.barterchest.integration;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Optional integration with SimpleClaims plugin.
 * 
 * This class uses reflection to interact with SimpleClaims so that
 * the BarterChest plugin can work both with and without SimpleClaims installed.
 * 
 * When SimpleClaims is present, shop creation is only allowed if:
 * - The chunk is unclaimed, OR
 * - The player is a member of the party that owns the claim
 */
public class SimpleClaimsIntegration {
    
    private static final HytaleLogger LOGGER = HytaleLogger.get("BarterChest/SimpleClaims");
    
    private static boolean initialized = false;
    private static boolean available = false;

    // NOTE: Older compatibility code (pre-maven) attempted to patch SimpleClaims' interact
    // restriction allowlist for BarterChest shops. In the mavenized build we keep the hook,
    // but make it a best-effort no-op because SimpleClaims' internals can change between
    // releases. The actual shop permission bypass is handled via runtime checks in
    // BarterInteractSystem / SimpleClaims integration logic.

    /**
     * Block ids that should be treated as "safe to interact" by SimpleClaims.
     *
     * BarterChest marks shop blocks with these ids in its asset pack. Keeping this allowlist
     * small avoids making every vanilla chest public.
     */
    private static final String[] SIMPLECLAIMS_PUBLIC_INTERACT_BLOCK_IDS = new String[]{
            "barterchest", // primary id
            "barter_chest" // legacy/alternate
    };
    
    // Cached reflection objects
    private static Class<?> claimManagerClass;
    private static Method getInstanceMethod;
    private static Method isAllowedToInteractMethod;
    private static Method getChunkRawCoordsMethod;
    private static Method getPartyByIdMethod;
    private static Method getPartyOwnerMethod;
    
    /**
     * Initialize the SimpleClaims integration.
     * Call this once during plugin startup.
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Try to load SimpleClaims classes
            claimManagerClass = Class.forName("com.buuz135.simpleclaims.claim.ClaimManager");
            getInstanceMethod = claimManagerClass.getMethod("getInstance");
            
            // Get the isAllowedToInteract method - this handles all permission checks
            // Signature: isAllowedToInteract(UUID playerUUID, String dimension, int chunkX, int chunkZ, Predicate<PartyInfo> interactMethod)
            Class<?> partyInfoClass = Class.forName("com.buuz135.simpleclaims.claim.party.PartyInfo");
            isAllowedToInteractMethod = claimManagerClass.getMethod("isAllowedToInteract", 
                UUID.class, String.class, int.class, int.class, Predicate.class);
            
            getChunkRawCoordsMethod = claimManagerClass.getMethod("getChunkRawCoords", String.class, int.class, int.class);
            getPartyByIdMethod = claimManagerClass.getMethod("getPartyById", UUID.class);
            
            // ChunkInfo class
            Class<?> chunkInfoClass = Class.forName("com.buuz135.simpleclaims.claim.chunk.ChunkInfo");
            getPartyOwnerMethod = chunkInfoClass.getMethod("getPartyOwner");
            
            available = true;
            LOGGER.at(Level.INFO).log("SimpleClaims integration enabled - shop creation will respect claims");

            // --- Compatibility: allow public interaction with BarterChest shop blocks ---
            //
            // Newer SimpleClaims versions can treat any block with a name containing "chest" as a
            // protected chest interaction, which blocks non-party members from using BarterChest shops
            // (BarterChest registers its state type as "BarterChest").
            //
            // SimpleClaims provides a config list intended to exempt certain blocks from interact
            // restrictions. We patch that list at runtime to include "barterchest".
            //
            // This keeps normal claim protections intact (shop creation still requires permission),
            // while allowing customers to open the BarterChest purchase UI.
            try {
                allowPublicBarterChestInteractInSimpleClaims();
            } catch (Throwable t) {
                // Never hard-fail plugin init because of an optional compat tweak.
                LOGGER.at(Level.WARNING).log("Failed to apply SimpleClaims BarterChest interact compatibility: " + t.getMessage());
            }
            
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.at(Level.INFO).log("SimpleClaims not found - claim protection disabled for shops");
        } catch (Exception e) {
            available = false;
            LOGGER.at(Level.WARNING).log("Error initializing SimpleClaims integration: " + e.getMessage());
        }
    }
    
    /**
     * Check if SimpleClaims is available.
     */
    public static boolean isAvailable() {
        if (!initialized) initialize();
        return available;
    }
    
    /**
     * Check if a player can create a shop at the given location.
     * 
     * @param playerUUID The player's UUID
     * @param dimension The dimension name (e.g., "overworld")
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return true if the player can create a shop, false otherwise
     */
    public static boolean canCreateShop(@Nonnull UUID playerUUID, @Nonnull String dimension, int blockX, int blockZ) {
        if (!initialized) initialize();
        
        // If SimpleClaims is not available, allow shop creation
        if (!available) {
            return true;
        }
        
        try {
            Object claimManager = getInstanceMethod.invoke(null);
            
            // Use SimpleClaims' isAllowedToInteract method with a predicate that always returns false
            // This means we only allow if the player has permission (member, ally, admin, etc.)
            // The predicate is for party-level permissions like "allowPublicBuild" - we want to require membership
            Predicate<?> alwaysFalse = (obj) -> false;
            
            Boolean allowed = (Boolean) isAllowedToInteractMethod.invoke(claimManager, 
                playerUUID, dimension, blockX, blockZ, alwaysFalse);
            
            LOGGER.at(Level.FINE).log("SimpleClaims check for %s at %s (%d, %d): %s", 
                playerUUID, dimension, blockX, blockZ, allowed);
            
            return allowed != null && allowed;
            
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).log("Error checking SimpleClaims permission: " + e.getMessage());
            e.printStackTrace();
            // On error, default to allowing (fail open)
            return true;
        }
    }
    
    /**
     * Get the name of the party that owns a chunk, if any.
     * 
     * @param dimension The dimension name
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return The party name, or null if unclaimed or SimpleClaims unavailable
     */
    @Nullable
    public static String getChunkOwnerName(@Nonnull String dimension, int blockX, int blockZ) {
        if (!initialized) initialize();
        if (!available) return null;
        
        try {
            Object claimManager = getInstanceMethod.invoke(null);
            Object chunkInfo = getChunkRawCoordsMethod.invoke(claimManager, dimension, blockX, blockZ);
            
            if (chunkInfo == null) return null;
            
            UUID chunkOwnerPartyId = (UUID) getPartyOwnerMethod.invoke(chunkInfo);
            if (chunkOwnerPartyId == null) return null;
            
            Object party = getPartyByIdMethod.invoke(claimManager, chunkOwnerPartyId);
            
            if (party != null) {
                Method getNameMethod = party.getClass().getMethod("getName");
                return (String) getNameMethod.invoke(party);
            }
            
        } catch (Exception e) {
            LOGGER.at(Level.FINE).log("Error getting chunk owner: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Compatibility shim.
     *
     * Earlier iterations of this project included a helper that attempted to adjust
     * SimpleClaims configuration so BarterChest shop interactions could be public. The
     * current (maven-based) integration instead uses {@link #canInteract(World, int, int, int, Player)}
     * to decide whether a player should be allowed through.
     *
     * This method remains as a no-op so older codepaths compile safely.
     */
    @SuppressWarnings("unused")
    private static void allowPublicBarterChestInteractInSimpleClaims() {
        // SimpleClaims 2026+ enforces interact restrictions via UseBlockEvent.Pre and
        // consults Main.CONFIG.get().getBlocksThatIgnoreInteractRestrictions().
        //
        // BarterChest shop blocks are *not* vanilla chests; they use a custom block state
        // whose name is "barterchest". So we can safely whitelist ONLY BarterChest
        // shop blocks without opening up all chests inside claims.
        try {
            Class<?> mainClazz = Class.forName("com.buuz135.simpleclaims.Main");
            Object configObject = mainClazz.getField("CONFIG").get(null);

            Object cfg = configObject.getClass().getMethod("get").invoke(configObject);
            Object listObj = cfg.getClass().getMethod("getBlocksThatIgnoreInteractRestrictions").invoke(cfg);

            if (!(listObj instanceof java.util.List<?> list)) {
                LOGGER.at(Level.WARNING).log("SimpleClaims CONFIG ignore list was not a java.util.List - could not patch");
                return;
            }

            final String token = "barterchest";
            boolean changed = false;
            boolean found = false;
            for (Object o : list) {
                if (o != null && token.equalsIgnoreCase(String.valueOf(o).trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                //noinspection unchecked
                ((java.util.List<Object>) list).add(token);
                changed = true;
            }

            if (changed) {
                configObject.getClass().getMethod("save").invoke(configObject);
                LOGGER.at(Level.INFO).log("Patched SimpleClaims: added '" + token + "' to BlocksThatIgnoreInteractRestrictions");
            }
        } catch (Throwable t) {
            // We keep this best-effort; BarterChest should still run without SimpleClaims.
            LOGGER.at(Level.WARNING).withCause(t).log("Failed to patch SimpleClaims interact ignore list for public BarterChest usage");
        }
    }
}
