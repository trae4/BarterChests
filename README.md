# BarterChest

A player shop plugin for Hytale servers that allows players to create chest-based shops for buying and selling items using configurable currencies.

## Features

- **Player-Owned Shops**: Players can create their own shops using a Barter License item
- **GUI-Based Configuration**: Easy-to-use interface for setting up shop items, prices, and currency
- **Barter System**: Trade items for other items (e.g., gold bars, copper bars, or any custom currency)
- **Floating Item Display**: Shows the item being sold floating above the shop chest
- **Buy & Sell Support**: Shops can buy from players, sell to players, or both
- **Protection System**: Shop chests are protected from breaking and chest merging
- **Admin Tools**: Server admins can manage any shop with admin mode
- **SimpleClaims Integration**: Optional integration to respect land claims (requires SimpleClaims plugin)

## Installation

1. Build the plugin using Gradle: `./gradlew build`
2. Copy the resulting JAR and resources to your server's plugin directory
3. Restart the server

## Usage

### Creating a Shop

1. Obtain a **Barter License** item (given by admins or obtained through gameplay)
2. Place a **single chest** where you want your shop
3. Right-click the chest with the Barter License to convert it to a shop
4. The configuration GUI will open automatically

### Configuring Your Shop

The configuration GUI allows you to:

1. **Select Currency**: Choose from preset currencies (Copper, Iron, Silver, Gold bars) or use a custom item by holding it and clicking "Use Item in Hand"
2. **Set Buy Price**: The price customers pay to buy from your shop (use +/- buttons)
3. **Set Sell Price**: The price you pay customers who sell to your shop (use +/- buttons)
4. **Item to Sell**: Automatically detected from items in your chest, or set manually
5. **Save**: Saves your configuration and updates the floating display
6. **Remove Shop**: Converts the shop back to a regular chest

### Restocking Your Shop

- **Crouch + Right-click** on your shop to access the chest inventory directly
- Add items you want to sell
- Add currency to pay customers who sell to you

### Buying/Selling as a Customer

- **Right-click** a shop to open the shop interface
- Click **Buy** to purchase items (requires currency in your inventory)
- Click **Sell** to sell items to the shop (if the shop is buying)
- **Hold SHIFT** while clicking for 10x quantity transactions

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/barterchest admin` | Toggle admin mode for managing any shop | `barterchest.admin` |

### Admin Mode

When admin mode is enabled:
- Admins can access any shop's configuration GUI
- Admins can restock any shop (crouch + right-click)
- Admins can remove any shop

When admin mode is disabled:
- Admins interact with shops like regular customers

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `barterchest.admin` | Access to admin commands and shop management | OP |

## Configuration

The plugin stores configuration in `<universe>/BarterChest/config.json`:

```json
{
  "defaultCurrency": "Ingredient_Bar_Copper",
  "defaultCurrencies": [
    {"itemId": "Ingredient_Bar_Copper", "displayName": "Copper Bar"},
    {"itemId": "Ingredient_Bar_Iron", "displayName": "Iron Bar"},
    {"itemId": "Ingredient_Bar_Silver", "displayName": "Silver Bar"},
    {"itemId": "Ingredient_Bar_Gold", "displayName": "Gold Bar"}
  ],
  "licenseItemModel": "Items/Ingredient_Fabric_Scrap_Linen.fbx",
  "licenseItemTexture": "Items/Ingredient_Fabric_Scrap_Linen.png",
  "licenseItemName": "Barter License",
  "licenseItemDescription": "Use on a chest to create a barter shop"
}
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `defaultCurrency` | The default currency for new shops | `Ingredient_Bar_Copper` |
| `defaultCurrencies` | List of currency options shown in the shop config GUI | Copper, Iron, Silver, Gold bars |
| `licenseItemModel` | 3D model file for the Barter License item | `Items/Ingredient_Fabric_Scrap_Linen.fbx` |
| `licenseItemTexture` | Texture file for the Barter License item | `Items/Ingredient_Fabric_Scrap_Linen.png` |
| `licenseItemName` | Display name for the Barter License item | `Barter License` |
| `licenseItemDescription` | Description for the Barter License item | `Use on a chest to create a barter shop` |

**Note:** Changes to `licenseItem*` options require editing the item JSON file directly at `Server/Item/Items/Barter_License.json` and restarting the server, as item definitions are loaded at startup.

## SimpleClaims Integration

If SimpleClaims is installed, BarterChest will automatically:
- Prevent shop creation on land claimed by other parties
- Allow shop creation on unclaimed land
- Allow shop creation on land you or your party owns
- Respect ally permissions

No configuration needed - the integration is automatic when SimpleClaims is detected.

## Technical Details

### Block State

Shops use a custom `BarterChestBlockState` that extends `ItemContainerState`. This preserves the chest's inventory while adding shop functionality.

### Data Persistence

- Shop data is stored in the world's chunk data
- Configuration is stored in the universe folder
- All data persists across server restarts

### Protection

- **Break Protection**: Shops cannot be broken by players (DamageBlockEvent and BreakBlockEvent)
- **Merge Protection**: Chests cannot be placed adjacent to shops to prevent double-chest formation
- **Double Chest Prevention**: Double chests cannot be converted to shops

## Troubleshooting

### "Not Configured" showing on configured shop
- Ensure the shop has an item set (either from chest contents or manually)
- Ensure at least one price (buy or sell) is greater than 0
- Ensure a currency is selected

### Items not transferring correctly
- Check that item IDs match exactly (case-sensitive)
- Ensure the shop has stock (for buying) or space (for selling)
- Ensure customers have currency (for buying) or the item (for selling)

### Floating display not showing
- The shop must be configured with an item
- Try breaking and recreating the shop if issues persist

## License

This plugin is provided as-is for use with Hytale servers.

## Credits

Developed for Hytale server modding.
