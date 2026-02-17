package net.hytaletogether.barterchest.integration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based integration with CivicCore Economy plugin.
 * Provides virtual currency (wallet) support for BarterChest shops.
 * 
 * When a shop uses digital currency, transactions go through the player's
 * wallet instead of exchanging physical items.
 */
public class EconomyIntegration {
    
    /** Special currency ID that indicates digital/wallet currency */
    public static final String DIGITAL_CURRENCY_ID = "ECONOMY:DIGITAL";
    
    private static boolean initialized = false;
    private static boolean available = false;
    
    // Cached reflection references
    private static Object economyPlugin = null;
    private static Object economyAPI = null;
    
    // API methods (on EconomyAPI, not EconomyPlugin!)
    private static Method getBalanceMethod = null;
    private static Method hasBalanceMethod = null;
    private static Method withdrawMethod = null;
    private static Method depositMethod = null;
    private static Method withdrawWithReasonMethod = null;
    private static Method depositWithReasonMethod = null;
    
    // Plugin methods (on EconomyPlugin)
    private static Method formatMoneyMethod = null;
    private static Method getCurrencyNameMethod = null;
    private static Method getCurrencyNamePluralMethod = null;
    
    /**
     * Check if digital currency is being used.
     */
    public static boolean isDigitalCurrency(@Nullable String currencyId) {
        return DIGITAL_CURRENCY_ID.equals(currencyId);
    }
    
    /**
     * Initialize the economy integration by looking up the Economy plugin via reflection.
     */
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Get EconomyPlugin class and instance
            Class<?> pluginClass = Class.forName("com.civiccore.economy.EconomyPlugin");
            Method getInstanceMethod = pluginClass.getMethod("getInstance");
            economyPlugin = getInstanceMethod.invoke(null);
            
            if (economyPlugin == null) {
                System.out.println("[BarterChest] Economy plugin not yet initialized");
                initialized = false;
                return;
            }
            
            // Get EconomyAPI from the plugin - THIS IS THE KEY DIFFERENCE
            Method getAPIMethod = pluginClass.getMethod("getEconomyAPI");
            economyAPI = getAPIMethod.invoke(economyPlugin);
            
            if (economyAPI == null) {
                System.out.println("[BarterChest] Economy API not available");
                initialized = false;
                return;
            }
            
            // Cache API methods (these are on EconomyAPI, not EconomyPlugin!)
            Class<?> apiClass = economyAPI.getClass();
            getBalanceMethod = apiClass.getMethod("getBalance", UUID.class);
            hasBalanceMethod = apiClass.getMethod("hasBalance", UUID.class, long.class);
            withdrawMethod = apiClass.getMethod("withdraw", UUID.class, long.class);
            depositMethod = apiClass.getMethod("deposit", UUID.class, long.class);
            
            // Try to get notification-enabled methods
            try {
                withdrawWithReasonMethod = apiClass.getMethod("withdraw", UUID.class, long.class, String.class);
                depositWithReasonMethod = apiClass.getMethod("deposit", UUID.class, long.class, String.class);
            } catch (NoSuchMethodException e) {
                // Older API without notification support
                withdrawWithReasonMethod = null;
                depositWithReasonMethod = null;
            }
            
            // Cache plugin methods (these ARE on EconomyPlugin)
            try {
                formatMoneyMethod = pluginClass.getMethod("formatMoney", long.class);
            } catch (NoSuchMethodException e) {
                formatMoneyMethod = null;
            }
            
            try {
                getCurrencyNameMethod = pluginClass.getMethod("getCurrencyName");
            } catch (NoSuchMethodException e) {
                getCurrencyNameMethod = null;
            }
            
            try {
                getCurrencyNamePluralMethod = pluginClass.getMethod("getCurrencyNamePlural");
            } catch (NoSuchMethodException e) {
                getCurrencyNamePluralMethod = null;
            }
            
            available = true;
            System.out.println("[BarterChest] Economy integration initialized successfully");
            
        } catch (ClassNotFoundException e) {
            System.out.println("[BarterChest] CivicCore Economy plugin not found - digital currency disabled");
            available = false;
        } catch (Exception e) {
            System.err.println("[BarterChest] Error initializing economy integration: " + e.getMessage());
            e.printStackTrace();
            available = false;
        }
    }
    
    /**
     * Check if the economy integration is available.
     */
    public static boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }
    
    /**
     * Get the currency name from the economy plugin (e.g., "Orb" or "Coin").
     */
    @Nonnull
    public static String getCurrencyName() {
        if (!isAvailable()) {
            return "Coins";
        }
        
        try {
            if (getCurrencyNameMethod != null && economyPlugin != null) {
                Object result = getCurrencyNameMethod.invoke(economyPlugin);
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return "Coins";
    }
    
    /**
     * Get the plural currency name from the economy plugin (e.g., "Orbs" or "Coins").
     */
    @Nonnull
    public static String getCurrencyNamePlural() {
        if (!isAvailable()) {
            return "Coins";
        }
        
        try {
            if (getCurrencyNamePluralMethod != null && economyPlugin != null) {
                Object result = getCurrencyNamePluralMethod.invoke(economyPlugin);
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Fallback: try singular + "s"
        String singular = getCurrencyName();
        if (!singular.endsWith("s")) {
            return singular + "s";
        }
        return singular;
    }
    
    /**
     * Get the display name for digital currency (for UI).
     */
    @Nonnull
    public static String getDigitalCurrencyDisplayName() {
        if (!isAvailable()) {
            return "Digital Currency (Unavailable)";
        }
        return getCurrencyNamePlural() + " (Wallet)";
    }
    
    /**
     * Get a player's wallet balance.
     */
    public static long getBalance(@Nonnull UUID playerUUID) {
        if (!isAvailable()) {
            return 0;
        }
        
        try {
            // Call on economyAPI, not economyPlugin!
            Object result = getBalanceMethod.invoke(economyAPI, playerUUID);
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
        } catch (Exception e) {
            System.err.println("[BarterChest] Error getting balance: " + e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Check if a player has at least the specified amount.
     */
    public static boolean hasBalance(@Nonnull UUID playerUUID, long amount) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            Object result = hasBalanceMethod.invoke(economyAPI, playerUUID, amount);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            // Fallback to manual check
            return getBalance(playerUUID) >= amount;
        }
        
        return false;
    }
    
    /**
     * Withdraw currency from a player's wallet (with notification).
     * 
     * @param playerUUID The player's UUID
     * @param amount The amount to withdraw
     * @param reason The reason for the transaction (shown in notification)
     * @return true if successful
     */
    public static boolean withdraw(@Nonnull UUID playerUUID, long amount, @Nullable String reason) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Try notification method first - call on economyAPI!
            if (withdrawWithReasonMethod != null && reason != null) {
                Object result = withdrawWithReasonMethod.invoke(economyAPI, playerUUID, amount, reason);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
            
            // Fall back to basic method
            Object result = withdrawMethod.invoke(economyAPI, playerUUID, amount);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Exception e) {
            System.err.println("[BarterChest] Error withdrawing: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Deposit currency to a player's wallet (with notification).
     * 
     * @param playerUUID The player's UUID
     * @param amount The amount to deposit
     * @param reason The reason for the transaction (shown in notification)
     * @return true if successful
     */
    public static boolean deposit(@Nonnull UUID playerUUID, long amount, @Nullable String reason) {
        if (!isAvailable()) {
            return false;
        }
        
        try {
            // Try notification method first - call on economyAPI!
            if (depositWithReasonMethod != null && reason != null) {
                depositWithReasonMethod.invoke(economyAPI, playerUUID, amount, reason);
                return true;
            }
            
            // Fall back to basic method
            depositMethod.invoke(economyAPI, playerUUID, amount);
            return true;
        } catch (Exception e) {
            System.err.println("[BarterChest] Error depositing: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Format a currency amount for display (e.g., "100 Orbs").
     */
    @Nonnull
    public static String formatMoney(long amount) {
        if (!isAvailable()) {
            return amount + " Coins";
        }
        
        try {
            // formatMoney IS on EconomyPlugin
            if (formatMoneyMethod != null && economyPlugin != null) {
                Object result = formatMoneyMethod.invoke(economyPlugin, amount);
                if (result != null) {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // Ignore, use fallback
        }
        
        // Manual fallback
        String name = (amount == 1) ? getCurrencyName() : getCurrencyNamePlural();
        return amount + " " + name;
    }
}
