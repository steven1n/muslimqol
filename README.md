# MuslimQoL

[![Build](https://github.com/steven1n/muslimqol/actions/workflows/build.yml/badge.svg)](https://github.com/steven1n/muslimqol/actions/workflows/build.yml)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://neoforged.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.176-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Status](https://img.shields.io/badge/Status-v0.5.0--beta.1-blueviolet.svg)](https://github.com/steven1n/muslimqol)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A configurable Muslim-friendly quality-of-life framework for Minecraft.

> [!NOTE]
> **Project Status: v0.5.0-beta.1**  
> Minecraft **1.21.1** | **NeoForge** 21.1.x | **Java 21**  
> This version is a **Public Beta (`0.5.0-beta.1`)** covering food classification, mod compatibility packs, Qibla direction, offline prayer time calculation, Salah HUD & reminders, and dedicated-server client sync. It is not yet a final stable 1.0 release.

---

## Help Test v0.5.0-beta.1

Please report bugs, compatibility issues, and classification feedback through [GitHub Issues](https://github.com/steven1n/muslimqol/issues).

- **Testing Guide**: See [docs/testing/rc1-external-testing.md](docs/testing/rc1-external-testing.md) and [docs/testing/v0.1-test-matrix.md](docs/testing/v0.1-test-matrix.md) for test areas, scenarios, and reporting guidelines.

---

## Overview

MuslimQoL helps Muslim players manage dietary and observance-related gameplay preferences within Minecraft. The framework provides data-driven food classification, server-authoritative dietary controls, and client-private offline observance tools:

- **Food classification** into four distinct states (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`) with a five-tier priority cascade
- **Configurable consumption behavior** (`ALLOW`, `WARN`, `BLOCK`) enforced on the logical server
- **Dedicated server to client sync** (`muslimqol:classification_sync`) synchronizing server `DATAPACK` rules, `USER_OVERRIDE` entries, and consumption policies to connected clients
- **Tooltip indicators & inventory slot badges** on edible food items, with optional **JEI** and **EMI** recipe viewer compatibility
- **Pig and pork gameplay controls** (`NORMAL`, `NO_NATURAL_SPAWN`, `NO_PORK_DROPS`, `DISABLED_GAMEPLAY`) without removing vanilla registry objects
- **Multi-provider compatibility framework** and built-in compatibility packs for third-party food mods (**Farmer's Delight**, **Pam's HarvestCraft 2 — Food Core**)
- **Offline, client-private Qibla direction** bearing calculation and HUD compass indicator
- **Offline astronomical prayer time calculation** (`Fajr`, `Sunrise`, `Dhuhr`, `Asr`, `Maghrib`, `Isha`) with standard conventions and high-latitude safe-boundary rules
- **Salah Experience** with next-prayer HUD countdown and localized advance / prayer-start Toast notifications (`en_us` and `ar_sa`)
- **Dedicated server compatibility** with strict physical client isolation

---

## Food Classification States

Every food item resolves to one of four states:

| Status | Label | Description | Default Policy |
| :--- | :---: | :--- | :---: |
| **HALAL** | `✓ Halal` | Clearly classified as permitted by the active gameplay classification data (e.g., plant-based foods, honey, seafood). | `ALLOW` |
| **RESTRICTED** | `⛔ Restricted` | Configured as restricted (e.g., swine derivatives, carrion, toxic items). | `BLOCK` |
| **DOUBTFUL** | `⚠ Doubtful` | Classification is uncertain or intentionally marked doubtful (e.g., suspicious stew, fermented/unknown mixtures). | `WARN` |
| **UNKNOWN** | `? Unknown` | No reliable classification data is available (e.g., untagged meats or unclassified third-party mod items). | `ALLOW` |

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
- **Next-Prayer HUD & Countdown**: Displays the next obligatory prayer, its local time (`HH:mm`) in the configured prayer `ZoneId`, and a localized seconds-free countdown (`2h 05m`, `47m`, `<1m`) that crosses midnight and DST boundaries accurately using `Instant` and `Duration`.
- **Advance & Prayer-Start Toasts**: Configurable advance reminders (`0..60` minutes, default `10`; `0 = no advance notification`), prayer-start notifications, and per-prayer notification switches delivered via localized Minecraft Toasts (`en_us` and `ar_sa`).
- **Deterministic Qibla Coexistence**: Positions cleanly below the Qibla HUD when both overlays are active, or at the top-left when Qibla HUD is hidden.

For full behavior, configuration, and privacy details, see [docs/features/salah-experience.md](docs/features/salah-experience.md).

---

## Roadmap Status

- **0.1 Food Classification**: Complete (Released)
- **0.2 Compatibility**: Complete
- **0.3 Qibla**: Complete
- **0.4 Prayer Calculation**: Complete
- **0.5 Salah Experience**: Current (`0.5.0-beta.1`)

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
4. Adjust dietary, Qibla, and prayer preferences in your config files as desired.

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
- **Pam's HarvestCraft 2 — Food Core**: Audited and classified for **Pam's HarvestCraft 2 — Food Core (`1.21.1-1.0.5`)**.
  - **Namespace**: `muslimqol_pamhc2foodcore`
  - **Zero Java Dependencies**: Provided strictly through data-driven compatibility definitions; no code dependency on Pam's HarvestCraft 2.
  - **Coverage**: 100% coverage across all 180 edible items (113 Halal, 17 Restricted, 26 Doubtful, 24 Unknown; 0 unclassified).
  - **Behavior**: Automatically activated when `pamhc2foodcore` is installed; safely skipped when absent.
  - **Documentation**: See [docs/testing/pams-food-core-compatibility.md](docs/testing/pams-food-core-compatibility.md) for audit details.

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

### Client Display, Qibla & Prayer Preferences (`config/muslimqol-client.toml`)
```toml
[food]
show_tooltips = true
show_inventory_icons = true

[display]
show_halal_icon = true
show_restricted_icon = true
show_doubtful_icon = true
show_unknown_icon = false

[qibla]
enabled = true
location_configured = false
latitude = 0.0
longitude = 0.0
hud_enabled = true

[prayer]
enabled = true
zone_id = ""
calculation_method = "MUSLIM_WORLD_LEAGUE"
custom_fajr_angle = 18.0
custom_isha_angle = 17.0
asr_method = "STANDARD"
high_latitude_rule = "MIDDLE_OF_NIGHT"
fajr_adjustment_minutes = 0
sunrise_adjustment_minutes = 0
dhuhr_adjustment_minutes = 0
asr_adjustment_minutes = 0
maghrib_adjustment_minutes = 0
isha_adjustment_minutes = 0
salah_hud_enabled = true
notifications_enabled = true
advance_notification_enabled = true
advance_notification_minutes = 10
start_notification_enabled = true
notify_fajr = true
notify_dhuhr = true
notify_asr = true
notify_maghrib = true
notify_isha = true
```

---

## In-Game Commands

All commands are registered under `/muslimqol` in `MuslimQolCommands`:

- `/muslimqol status` — Displays active consumption policies and pig gameplay rules.
- `/muslimqol classify <item>` — Queries the resolved status, winning provider/rule, candidate list, and conflict state for a specified item.
- `/muslimqol providers` — Lists active classification providers and compatibility packs (active vs. skipped).
- `/muslimqol compat` — Displays compatibility pack verification diagnostics (target version, installed version, verification status).
- `/muslimqol reload` — Reloads configuration overrides and broadcasts updated classification snapshots to connected clients (Permission level 2).

---

## Known Limitations (已知限制)

- **Classification Sync Truncation (分类同步超限截断)**: When server classification data exceeds network payload caps (`MAX_DATAPACK_ITEMS = 8,192` or `MAX_PAYLOAD_BYTES = 960 KiB`), excess entries are truncated deterministically in lexicographical order; truncated namespaces may display as `UNKNOWN` on the client while server-side consumption enforcement remains 100% active (分类同步超限时按字典序截断，被截断的命名空间在客户端可能显示 Unknown).
- **Shared Observer Coordinates (观察者坐标共用)**: Observer coordinates (`latitude`, `longitude`, `location_configured`) are configured under `[qibla]` in `muslimqol-client.toml` and shared by both Qibla direction and prayer time calculation (观察者坐标配置在 `[qibla]` 下，礼拜时间共用).
- **Fixed Salah HUD Position (Salah HUD 位置固定)**: The Salah HUD overlay position (top-left, below Qibla HUD when both are visible) is currently fixed and not user-configurable (Salah HUD 位置固定，暂不可配置).
- **English Command Output (`/muslimqol` 命令输出语言)**: Diagnostic output from `/muslimqol` subcommands (`classify`, `providers`, `compat`) currently uses English formatting (`/muslimqol` 命令输出目前是英文).
- **Non-Authoritative Calculation Tool (非宗教权威免责声明)**: Prayer time calculation and Salah reminders are astronomical calculation and scheduling aids only, not a religious authority, and do not replace local mosque schedules or personal religious guidance (礼拜时间仅为计算与提醒工具，不是宗教权威).

---

## Testing & Verification

MuslimQoL undergoes continuous automated and runtime verification:

- **Automated Tests**: See CI (`./gradlew test build` JUnit 5 suite and `python3 -m unittest discover -s tools/compatibility/tests -t .` compatibility audit/provenance suite; 测试数量见 CI).
- **Dedicated Server**: Server startup, mod discovery, S2C classification sync registration, client class isolation, and clean shutdown verified via `./scripts/run_server_test.sh`.
- **Consumption & Pig Policies**: Server-authoritative `BLOCK`, `WARN`, and `ALLOW` policies and non-destructive pig spawn/drop controls verified.
- **Datapack & Config Reloading**: Transactional runtime reload via `/reload`, `/muslimqol reload`, and `ModConfigEvent.Reloading` verified.

### 手动验收状态 (Manual Verification Status)

- Salah HUD、提醒、Qibla：维护者已于 2026-09-30 在图形客户端手动验证通过。
- JEI/EMI 悬停：【请维护者填写：已验证 / 未验证】（未手动验证）。
- 食物 tooltip、栏位图标：【请维护者填写：已验证 / 未验证】（未手动验证）。

Detailed test logs and evidence are documented in [docs/testing/v0.1-test-matrix.md](docs/testing/v0.1-test-matrix.md) and [docs/testing/dedicated-server-sync-reproduction.md](docs/testing/dedicated-server-sync-reproduction.md).

---

## Development

MuslimQoL is built using NeoForge ModDevGradle and targets Java 21.

```bash
# Clone the repository
git clone https://github.com/steven1n/muslimqol.git
cd muslimqol

# Run automated JUnit tests
./gradlew test

# Run Python compatibility tooling tests
python3 -m unittest discover -s tools/compatibility/tests -t .

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
