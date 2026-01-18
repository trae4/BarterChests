package com.example.barterchest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Constants;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration manager for BarterChest plugin.
 * Handles loading and saving of currency options and other settings.
 */
public class BarterConfig {
    
    private static final Path CONFIG_DIR = Constants.UNIVERSE_PATH.resolve("BarterChest");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static BarterConfig instance;
    
    // Configuration fields
    private String defaultCurrency = "Ingredient_Bar_Copper";
    private List<CurrencyOption> defaultCurrencies = new ArrayList<>();
    
    // Barter License item appearance (for server managers to customize)
    private String licenseItemModel = "Items/Ingredient_Fabric_Scrap_Linen.fbx";
    private String licenseItemTexture = "Items/Ingredient_Fabric_Scrap_Linen.png";
    private String licenseItemName = "Barter License";
    private String licenseItemDescription = "Use on a chest to create a barter shop";
    
    // Crafting configuration
    private boolean craftingEnabled = true;
    private String craftingStation = "Workbench";
    private int craftingOutputQuantity = 1;
    private List<CraftingIngredient> craftingRecipe = new ArrayList<>();
    
    public static class CurrencyOption {
        public String itemId;
        public String displayName;
        
        public CurrencyOption() {}
        
        public CurrencyOption(String itemId, String displayName) {
            this.itemId = itemId;
            this.displayName = displayName;
        }
    }
    
    public static class CraftingIngredient {
        public String itemId;
        public int quantity;
        
        public CraftingIngredient() {}
        
        public CraftingIngredient(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
    
    private BarterConfig() {
        // Set defaults - use item IDs without namespace prefix
        defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Copper", "Copper Bar"));
        defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Iron", "Iron Bar"));
        defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Silver", "Silver Bar"));
        defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Gold", "Gold Bar"));
        
        // Default crafting recipe: 5x Linen Fabric + 1x Gold Bar = 1x Barter License
        craftingRecipe.add(new CraftingIngredient("Ingredient_Fabric_Scrap_Linen", 5));
        craftingRecipe.add(new CraftingIngredient("Ingredient_Bar_Gold", 1));
    }
    
    public static BarterConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }
    
    /**
     * Alias for get() for compatibility.
     */
    public static BarterConfig getInstance() {
        return get();
    }
    
    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, BarterConfig.class);
                if (instance == null) {
                    instance = new BarterConfig();
                }
                // Ensure defaults are set if missing
                if (instance.defaultCurrencies == null || instance.defaultCurrencies.isEmpty()) {
                    instance.defaultCurrencies = new ArrayList<>();
                    instance.defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Copper", "Copper Bar"));
                    instance.defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Iron", "Iron Bar"));
                    instance.defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Silver", "Silver Bar"));
                    instance.defaultCurrencies.add(new CurrencyOption("Ingredient_Bar_Gold", "Gold Bar"));
                }
                if (instance.defaultCurrency == null) {
                    instance.defaultCurrency = "Ingredient_Bar_Copper";
                }
                if (instance.licenseItemModel == null) {
                    instance.licenseItemModel = "Items/Ingredient_Fabric_Scrap_Linen.fbx";
                }
                if (instance.licenseItemTexture == null) {
                    instance.licenseItemTexture = "Items/Ingredient_Fabric_Scrap_Linen.png";
                }
                if (instance.licenseItemName == null) {
                    instance.licenseItemName = "Barter License";
                }
                if (instance.licenseItemDescription == null) {
                    instance.licenseItemDescription = "Use on a chest to create a barter shop";
                }
                // Crafting defaults
                if (instance.craftingStation == null) {
                    instance.craftingStation = "Workbench";
                }
                if (instance.craftingRecipe == null || instance.craftingRecipe.isEmpty()) {
                    instance.craftingRecipe = new ArrayList<>();
                    instance.craftingRecipe.add(new CraftingIngredient("Ingredient_Fabric_Scrap_Linen", 5));
                    instance.craftingRecipe.add(new CraftingIngredient("Ingredient_Bar_Gold", 1));
                }
                if (instance.craftingOutputQuantity <= 0) {
                    instance.craftingOutputQuantity = 1;
                }
                System.out.println("[BarterChest] Loaded config from " + CONFIG_FILE);
            } catch (Exception e) {
                System.err.println("[BarterChest] Error loading config: " + e.getMessage());
                instance = new BarterConfig();
            }
        } else {
            instance = new BarterConfig();
            save();
        }
    }
    
    public static void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
                System.out.println("[BarterChest] Saved config to " + CONFIG_FILE);
            }
        } catch (Exception e) {
            System.err.println("[BarterChest] Error saving config: " + e.getMessage());
        }
    }
    
    public String getDefaultCurrency() {
        return defaultCurrency;
    }
    
    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }
    
    public List<CurrencyOption> getDefaultCurrencies() {
        return defaultCurrencies;
    }
    
    public void setDefaultCurrencies(List<CurrencyOption> currencies) {
        this.defaultCurrencies = currencies;
    }
    
    public String getLicenseItemModel() {
        return licenseItemModel;
    }
    
    public void setLicenseItemModel(String licenseItemModel) {
        this.licenseItemModel = licenseItemModel;
    }
    
    public String getLicenseItemTexture() {
        return licenseItemTexture;
    }
    
    public void setLicenseItemTexture(String licenseItemTexture) {
        this.licenseItemTexture = licenseItemTexture;
    }
    
    public String getLicenseItemName() {
        return licenseItemName;
    }
    
    public void setLicenseItemName(String licenseItemName) {
        this.licenseItemName = licenseItemName;
    }
    
    public String getLicenseItemDescription() {
        return licenseItemDescription;
    }
    
    public void setLicenseItemDescription(String licenseItemDescription) {
        this.licenseItemDescription = licenseItemDescription;
    }
    
    // Crafting getters/setters
    
    public boolean isCraftingEnabled() {
        return craftingEnabled;
    }
    
    public void setCraftingEnabled(boolean craftingEnabled) {
        this.craftingEnabled = craftingEnabled;
    }
    
    public String getCraftingStation() {
        return craftingStation;
    }
    
    public void setCraftingStation(String craftingStation) {
        this.craftingStation = craftingStation;
    }
    
    public int getCraftingOutputQuantity() {
        return craftingOutputQuantity;
    }
    
    public void setCraftingOutputQuantity(int craftingOutputQuantity) {
        this.craftingOutputQuantity = craftingOutputQuantity;
    }
    
    public List<CraftingIngredient> getCraftingRecipe() {
        return craftingRecipe;
    }
    
    public void setCraftingRecipe(List<CraftingIngredient> craftingRecipe) {
        this.craftingRecipe = craftingRecipe;
    }
    
    /**
     * Get the price increment for +/- buttons in the config UI.
     */
    public int getPriceIncrement() {
        return 1; // Default increment of 1
    }
    
    /**
     * Get the display name for a currency item ID.
     * Returns the configured display name if found, otherwise formats the item ID.
     */
    public String getCurrencyDisplayName(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return "Unknown";
        }
        
        // Check configured currencies
        for (CurrencyOption currency : defaultCurrencies) {
            if (currency.itemId != null && currency.itemId.equals(itemId)) {
                return currency.displayName;
            }
        }
        
        // Format the item ID as a display name
        // Remove namespace prefix if present
        String name = itemId;
        if (name.contains(":")) {
            name = name.substring(name.indexOf(":") + 1);
        }
        
        // Replace underscores with spaces and title case
        name = name.replace("_", " ");
        
        // Simple title case
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
}
