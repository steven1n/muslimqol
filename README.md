# MuslimQoL

[![Build](https://github.com/steven1n/muslimqol/actions/workflows/build.yml/badge.svg)](https://github.com/steven1n/muslimqol/actions/workflows/build.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://neoforged.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.176-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Status](https://img.shields.io/badge/Status-v0.1.0--rc1-blueviolet.svg)](https://github.com/steven1n/muslimqol)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A configurable Muslim-friendly quality-of-life framework for Minecraft.

> [!NOTE]
> **Project Status: v0.1.0-rc1**  
> Minecraft **1.21.1** | **NeoForge** 21.1.x | **Java 21**  
> This version is an initial **Release Candidate** focusing on core dietary classification and gameplay preferences. It is not yet a final stable 1.0 release.

---

## Overview

MuslimQoL helps Muslim players manage food-related gameplay preferences within Minecraft. The framework provides flexible, data-driven food classification and server-authoritative observance controls.

The current v0.1 release focuses strictly on:
- **Food classification** into four distinct states
- **Configurable dietary gameplay preferences**
- **Halal / Restricted / Doubtful / Unknown labels**
- **Tooltip indicators** on edible food items
- **Inventory slot indicators** in containers and hotbars
- **Configurable consumption behavior** (`ALLOW`, `WARN`, `BLOCK`)
- **Pig and pork gameplay controls** without removing vanilla objects
- **Datapack extensibility** for third-party food mods and custom packs
- **Dedicated server compatibility** with strict physical client isolation

---

## Food Classification States

Every food item resolves to one of four states:

| Status | Label | Description | Default Policy |
| :--- | :---: | :--- | :---: |
| **HALAL** | `✓ Halal` | Clearly classified as permitted by the active gameplay classification data (e.g., plant-based foods, honey, seafood). | `ALLOW` |
| **RESTRICTED** | `⛔ Restricted` | Configured as restricted (e.g., swine derivatives, carrion, toxic items). | `BLOCK` |
| **DOUBTFUL** | `⚠ Doubtful` | Classification is uncertain or intentionally marked doubtful (e.g., suspicious stew, toxic potato). | `WARN` |
| **UNKNOWN** | `? Unknown` | No reliable classification data is available (e.g., untagged meats or third-party mod items). | `ALLOW` |

> [!IMPORTANT]
> **Observance Disclaimer**  
> MuslimQoL provides configurable gameplay classifications and observance-oriented tools. It is not a halal certification authority and does not replace personal religious guidance. UI terminology reflects configurable gameplay states.

Classification queries follow a deterministic five-tier cascade:
```
USER_OVERRIDE  →  DATAPACK  →  ITEM_TAG  →  BUILTIN  →  UNKNOWN
```
When uncertain, items always safely resolve to `UNKNOWN` without guessing.

---

## Screenshots

> Screenshots coming before the first public mod-platform release.

See [docs/media/README.md](docs/media/README.md) for future media updates.

---

## Installation

### Requirements
- **Minecraft**: `1.21.1`
- **Mod Loader**: **NeoForge** `21.1.x`
- **Java**: `Java 21` or higher

### Steps
1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.
2. Place the MuslimQoL JAR in your `.minecraft/mods` directory.
3. Launch Minecraft.
4. Adjust dietary and display preferences in your config files as desired.

---

## Datapack Integration

Compatibility datapacks can classify items using standard item tags or MuslimQoL JSON metadata without modifying Java code.

### Example: Classifying Pork as Restricted via Item Tag

Create `data/<namespace>/tags/item/food/restricted.json`:
```json
{
  "replace": false,
  "values": [
    "minecraft:porkchop",
    "minecraft:cooked_porkchop"
  ]
}
```

For advanced JSON metadata schemas and dynamic reloading, see [docs/datapack-api.md](docs/datapack-api.md).

---

## Configuration

### Server Gameplay Preferences (`config/muslimqol-common.toml`)
```toml
[food]
halal_policy = "ALLOW"
restricted_policy = "BLOCK"
doubtful_policy = "WARN"
unknown_policy = "ALLOW"
user_overrides = []

[pigs]
# Options: NORMAL, NO_NATURAL_SPAWN, NO_PORK_DROPS, DISABLED_GAMEPLAY
policy = "NORMAL"
```

### Client Display Preferences (`config/muslimqol-client.toml`)
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

## In-Game Commands

- `/muslimqol status` — Displays active consumption policies and pig gameplay rules.
- `/muslimqol classify <item>` — Queries the status, source, and reason for a specified item.
- `/muslimqol reload` — Reloads configuration overrides (Permission level 2).

---

## Testing & Verification

The v0.1.0-rc1 implementation has undergone comprehensive runtime and automated verification:

- **Automated Tests**: 21 unit and regression tests passing across priority resolution, datapack JSON schemas, localization parity, and policy logic.
- **Dedicated Server**: Server startup, mod discovery, client isolation, and clean shutdown verified via live development server execution.
- **Client Runtime**: Graphics pipeline, main menu, and singleplayer world creation/saving verified.
- **Consumption Policies**: Server-authoritative `BLOCK`, `WARN`, and `ALLOW` verified across main hand and off hand.
- **Pig Policies**: Spawning controls and drop suppression verified while preserving vanilla registries.
- **Datapack Reloading**: Dynamic runtime reload via `/reload` and reload listener verified.

Detailed test logs and evidence are documented in the [v0.1 Test Matrix](docs/testing/v0.1-test-matrix.md).

---

## Development

MuslimQoL is built using NeoForge ModDevGradle and targets Java 21.

```bash
# Clone the repository
git clone https://github.com/steven1n/muslimqol.git
cd muslimqol

# Run automated tests
./gradlew test

# Compile and package mod jar
./gradlew build

# Launch client test environment
./gradlew runClient

# Launch dedicated server test environment
./gradlew runServer
```

---

## Contributing & Security

- Contributions: Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening pull requests.
- Security: Please review [SECURITY.md](SECURITY.md) for our vulnerability reporting policy.

---

## License

This project is licensed under the [MIT License](LICENSE).
