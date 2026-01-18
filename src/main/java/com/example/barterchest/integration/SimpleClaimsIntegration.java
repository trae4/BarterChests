package com.example.barterchest.integration;

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
}
