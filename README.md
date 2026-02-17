# BarterChest

A Hytale server plugin that lets players create chest-based barter shops. Players craft a **Barter License**, use it on a chest, and configure what they want to sell and for which currency — all through an in-game UI.

## Features

- **Player-owned shops** — Place a chest, apply a Barter License, set your prices, and you're open for business.
- **Configurable currencies** — Server owners define which items can be used as currency (defaults: Copper, Iron, Silver, Gold bars).
- **Craftable Barter License** — Recipe and crafting station are fully configurable via `config.json`.
- **In-game shop UI** — Buyers browse listings, adjust quantities, and purchase directly from the shop page.
- **Admin config UI** — Shop owners configure prices, currency, and stock through a dedicated settings page.
- **Floating item displays** — Shops show a floating preview of the item being sold, auto-refreshed on a configurable timer.
- **Shop protection** — Shops cannot be broken or merged by non-owners. A "shop plate" block is placed on top for public interaction through claim systems.
- **SimpleClaims integration** — Optional compatibility with SimpleClaims so shops work correctly inside claimed areas.
- **Economy integration** — Optional hook into CivicCore Economy.
- **Per-player shop limits** — Configurable max shops per player (0 = unlimited).
- **Admin tools** — Commands for toggling admin mode, listing all shops, teleporting to shops, auditing orphaned data, force-unregistering shops, and cleaning up display items.

## Commands

| Command | Description |
|---|---|
| `/barterchest` | Show help |
| `/barterchest admin` | Toggle admin mode |
| `/barterchest adminshops` | List all shops with teleport |
| `/barterchest cleanup` | Remove orphaned display items |
| `/barterchest audit` | Scan for orphaned shop protections |
| `/barterchest unregister [x y z]` | Force-unregister a shop |

## Building

**Requirements:** Java 17+, Gradle

The project uses the official Hytale Maven repository for the Server API.

```
gradle build
```

The shadow jar (with bundled dependencies) is output to `build/libs/`.

## Configuration

On first run the plugin creates `<universe>/BarterChest/config.json` with defaults:

```jsonc
{
  "defaultCurrency": "Ingredient_Bar_Copper",
  "defaultCurrencies": [
    { "itemId": "Ingredient_Bar_Copper", "displayName": "Copper Bar" },
    { "itemId": "Ingredient_Bar_Iron",   "displayName": "Iron Bar" },
    { "itemId": "Ingredient_Bar_Silver", "displayName": "Silver Bar" },
    { "itemId": "Ingredient_Bar_Gold",   "displayName": "Gold Bar" }
  ],
  "licenseItemName": "Barter License",
  "licenseItemDescription": "Use on a chest to create a barter shop",
  "craftingEnabled": true,
  "craftingStation": "Workbench",
  "craftingOutputQuantity": 1,
  "craftingRecipe": [
    { "itemId": "Ingredient_Fabric_Scrap_Linen", "quantity": 5 },
    { "itemId": "Ingredient_Bar_Gold", "quantity": 1 }
  ],
  "shopPlateBlockIds": ["Deco_Plate", "deco_plate"],
  "maxShopsPerPlayer": 0
}
```

## License

[MIT](LICENSE)
