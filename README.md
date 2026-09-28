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

## Help Test v0.1.0-rc1

The first public release candidate is available for testing.

- **Download**: [GitHub Releases (v0.1.0-rc1)](https://github.com/steven1n/muslimqol/releases/tag/v0.1.0-rc1)
- **Testing Guide**: See [docs/testing/rc1-external-testing.md](docs/testing/rc1-external-testing.md) for test areas, scenarios, and reporting guidelines.

Please report bugs and classification issues through [GitHub Issues](https://github.com/steven1n/muslimqol/issues).

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

## Qibla Direction

MuslimQoL includes an offline, client-private Qibla direction indicator:

- **Great-circle bearing calculation**: Spherical initial bearing from manually configured observer coordinates to the Kaaba (`21.4225° N, 39.8262° E`).
- **Client-side privacy**: Coordinates are configured only on the client (`muslimqol-client.toml`), never sent over the network or stored on multiplayer servers.
- **Offline operation**: Zero external API calls, IP lookups, or GPS dependencies.
- **HUD direction indicator**: Minimal, unobtrusive on-screen bearing and relative directional compass marker.
- **World orientation convention**: Maps Minecraft North (`-Z`) to geographic North (`0°`).

For full mathematical specifications, privacy model, and configuration instructions, see [docs/features/qibla.md](docs/features/qibla.md).

---

## Prayer Time Calculation

MuslimQoL 0.4 provides an offline, privacy-preserving daily prayer-time calculation core and client schedule service:

- **Six Daily Solar & Prayer Events**: Computes `Fajr`, `Sunrise` (`isObligatoryPrayer = false`), `Dhuhr`, `Asr`, `Maghrib`, and `Isha` using NOAA / Jean Meeus solar position equations.
- **Configurable Calculation Methods**: Supports `MUSLIM_WORLD_LEAGUE`, `EGYPTIAN`, `KARACHI`, `NORTH_AMERICA`, `KUWAIT`, `SINGAPORE`, `DUBAI`, and `CUSTOM` twilight angles, plus `STANDARD` and `HANAFI` Asr methods.
- **High-Latitude & Polar Resilience**: Supports `NONE`, `MIDDLE_OF_NIGHT`, `SEVENTH_OF_NIGHT`, and `TWILIGHT_ANGLE` safe-boundary and fallback rules across `sunset -> next sunrise`, returning structured `UNAVAILABLE` moments during polar day/night without sentinel timestamps.
- **Client-Only Privacy**: Reuses client-configured observer coordinates from `muslimqol-client.toml` with zero network I/O and no coordinate leakage in logs or `toString()`.

For full mathematical details, supported methods, and configuration options, see [docs/features/prayer-calculation.md](docs/features/prayer-calculation.md).

---

## Salah Experience (Reminders & Next-Prayer HUD)

MuslimQoL 0.5 adds client-private, offline Salah reminders and a compact next-prayer HUD overlay built on the 0.4 prayer calculation engine:

- **Five Obligatory Prayer Targets**: Tracks `Fajr`, `Dhuhr`, `Asr`, `Maghrib`, and `Isha` (`Sunrise` is never treated as a Salah reminder target).
- **Next-Prayer HUD & Countdown**: Displays the next obligatory prayer, its local time (`HH:mm`) in the configured prayer `ZoneId`, and a seconds-free countdown (`2h 05m`, `47m`, `<1m`) that crosses midnight and DST boundaries accurately using `Instant` and `Duration`.
- **Advance & Prayer-Start Toasts**: Configurable advance reminders (`0..60` minutes, default `10`; `0 = no advance notification`), prayer-start notifications, and per-prayer notification switches delivered via localized Minecraft Toasts (`en_us` and `ar_sa`).
- **Deterministic Qibla Coexistence**: Positions cleanly below the Qibla HUD when both overlays are active, or at the top-left when Qibla HUD is hidden.

For full behavior, configuration, and privacy details, see [docs/features/salah-experience.md](docs/features/salah-experience.md).

---

## Roadmap Status

- **0.1 Food Classification**: Complete
- **0.2 Compatibility**: Complete
- **0.3 Qibla**: Complete
- **0.4 Prayer Calculation**: Complete
- **0.5 Salah Experience**: Current

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

## Recipe Viewer Compatibility

MuslimQoL includes optional client compatibility with recipe viewers:
- **JEI (Just Enough Items)**: Dietary classification tooltips appear automatically in the JEI ingredient list and recipe views through NeoForge's standard `ItemTooltipEvent` pipeline. No JEI-specific code is used in MuslimQoL's production build. JEI is optional and never bundled into the release JAR. See [docs/jei-integration.md](docs/jei-integration.md) for details.
- **EMI**: Dietary classification tooltips appear automatically in the EMI index, recipe views, and favorites panels through NeoForge's standard `ItemTooltipEvent` pipeline. No EMI-specific code is used in MuslimQoL's production build. EMI is optional and never bundled into the release JAR. See [docs/emi-compatibility.md](docs/emi-compatibility.md) for details.

---

## Supported Compatibility Packs

MuslimQoL includes built-in compatibility packs for major third-party food mods. These packs activate dynamically when the target mod is present:

- **Farmer's Delight**: Fully audited and classified for **Farmer's Delight 1.3.4** (Minecraft 1.21.1 / NeoForge).
  - **Namespace**: `muslimqol_farmersdelight`
  - **Zero Java Dependencies**: Provided strictly through data-driven compatibility definitions; no code dependency on Farmer's Delight.
  - **Coverage**: 100% coverage across all 89 edible items (52 Halal, 11 Restricted, 5 Doubtful, 21 Unknown; 0 unclassified).
  - **Behavior**: Automatically activated when `farmersdelight` is installed; safely skipped when absent.
  - **Documentation**: See [docs/compatibility/farmers-delight-1.21.1.md](docs/compatibility/farmers-delight-1.21.1.md) for the complete item-by-item audit and classification table.

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
