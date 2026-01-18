package com.example.barterchest.admin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages admin mode state for players.
 * 
 * When admin mode is enabled, admins can access shop configuration
 * and management for any shop, not just their own.
 * 
 * When admin mode is disabled, admins interact with shops like customers.
 */
public class AdminModeManager {
    
    private static final Set<UUID> adminModeEnabled = new HashSet<>();
    
    /**
     * Check if admin mode is enabled for a player.
     */
    public static boolean isAdminModeEnabled(UUID playerUUID) {
        return adminModeEnabled.contains(playerUUID);
    }
    
    /**
     * Toggle admin mode for a player.
     * @return true if admin mode is now enabled, false if disabled
     */
    public static boolean toggleAdminMode(UUID playerUUID) {
        if (adminModeEnabled.contains(playerUUID)) {
            adminModeEnabled.remove(playerUUID);
            return false;
        } else {
            adminModeEnabled.add(playerUUID);
            return true;
        }
    }
    
    /**
     * Enable admin mode for a player.
     */
    public static void enableAdminMode(UUID playerUUID) {
        adminModeEnabled.add(playerUUID);
    }
    
    /**
     * Disable admin mode for a player.
     */
    public static void disableAdminMode(UUID playerUUID) {
        adminModeEnabled.remove(playerUUID);
    }
    
    /**
     * Called when a player disconnects to clean up their state.
     */
    public static void onPlayerDisconnect(UUID playerUUID) {
        adminModeEnabled.remove(playerUUID);
    }
}
