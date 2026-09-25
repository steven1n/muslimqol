# MuslimQoL

[![Build](https://github.com/muslimqol/muslimqol/actions/workflows/build.yml/badge.svg)](https://github.com/muslimqol/muslimqol/actions/workflows/build.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://neoforged.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.176-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A lightweight, extensible Minecraft mod designed to help Muslim players manage food-related gameplay preferences.

---

## Primary Design Philosophy

MuslimQoL behaves as:
> **A configurable accessibility and observance tool**, NOT a religious authority inside Minecraft.

The player or server administrator chooses their dietary preferences, and the mod reliably enforces those chosen preferences in gameplay. Terminology describes gameplay classifications only.

---

## Technical Baseline

- **Minecraft Version**: 1.21.1
- **Mod Loader**: NeoForge
- **Language**: Java 21
- **Build System**: Gradle 8.14.2

---

## V0.1 Features

### 1. Four-State Food Classification
Every food item resolves to one of four states:
- `✓ Halal`: Plant-based foods, honey, and seafood.
- `⛔ Restricted`: Swine products, carrion, and toxic items.
- `⚠ Doubtful`: Suspicious preparations or questionable items.
- `? Unknown`: Unclassified foods (including land animal meats lacking provenance tracking).

### 2. Strict Priority Cascade
Classification follows a deterministic priority order:
```
USER_OVERRIDE  →  DATAPACK  →  ITEM_TAG  →  BUILTIN  →  UNKNOWN
```
Fallback is always safely `UNKNOWN`.

### 3. Server-Authoritative Consumption Policy
Configure rules per classification:
- `ALLOW`: The player can consume the food normally.
- `WARN`: The player can consume the food, but receives an action-bar notice.
- `BLOCK`: Consumption is prevented server-side.

Default policies:
```toml
halal_policy = "ALLOW"
restricted_policy = "BLOCK"
doubtful_policy = "WARN"
unknown_policy = "ALLOW"
```

### 4. Non-Destructive Pork Gameplay Controls
Pigs and pork items are never deleted from registries:
- `NORMAL`: Default vanilla behavior.
- `NO_NATURAL_SPAWN`: Cancels natural pig spawning.
- `NO_PORK_DROPS`: Retains pigs, but suppresses pork drops.
- `DISABLED_GAMEPLAY`: Disables natural spawning and pork drops.

### 5. Client UX & Visual Indicators
- **Detailed Tooltips**: Clean status badge, classification reason, and consumption policy.
- **Inventory Overlays**: Status corner badges on food slots in hotbars, chests, and inventories.
- **Client Configuration**: Full toggle support for tooltips and individual icon filters.

### 6. Datapack Extensibility
Add support for third-party mods via item tags:
- `#muslimqol:food/halal`
- `#muslimqol:food/restricted`
- `#muslimqol:food/doubtful`

Or deploy custom JSON classification files under:
```text
data/<namespace>/muslimqol/food_classifications/<filename>.json
```

---

## Commands

- `/muslimqol status` — Displays active consumption policies and pig gameplay rules.
- `/muslimqol classify <item>` — Queries the status, source, and reason for an item.
- `/muslimqol reload` — Reloads configuration overrides (Permission level 2).

---

## Configuration

Server/gameplay configuration file: `config/muslimqol-common.toml`
```toml
[food]
halal_policy = "ALLOW"
restricted_policy = "BLOCK"
doubtful_policy = "WARN"
unknown_policy = "ALLOW"
user_overrides = []

[pigs]
policy = "NORMAL"
```

Client display configuration file: `config/muslimqol-client.toml`
```toml
[food]
show_tooltips = true
show_inventory_icons = true

[display]
show_halal_icon = true
show_restricted_icon = true
show_doubtful_icon = true
show_unknown_icon = false
```

---

## Explicitly Out of Scope for V0.1

To maintain focus and high software quality, the following features are reserved for future releases:
- Prayer time calculations & Adhan
- Qibla compass & coordinates
- Hijri calendar, Ramadan & fasting trackers
- Wudu, prayer mats, and mosque structures
- Livestock slaughter mechanics (planned for v0.7)
- Fabric loader support (planned for v0.8)

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/muslimqol/muslimqol.git
cd muslimqol

# Build the mod jar and run tests
./gradlew build
```

The resulting mod jar is generated in `build/libs/`.

---

## License

This project is licensed under the [MIT License](LICENSE).
