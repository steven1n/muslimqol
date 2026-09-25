# MuslimQoL v0.1.0-rc1 External Testing Guide

Thank you for helping test MuslimQoL! This guide provides instructions, test areas, and reporting templates for external testers evaluating the first release candidate (**v0.1.0-rc1**).

---

## 1. Tested Release

| Component | Target Version |
| :--- | :--- |
| **Mod Version** | `v0.1.0-rc1` (Pre-release) |
| **Minecraft** | `1.21.1` |
| **Mod Loader** | `NeoForge 21.1.x` (verified on `21.1.176`+) |
| **Java Runtime** | `Java 21` |

---

## 2. Download

Download the release candidate JAR from the official GitHub Releases page:

- **Release Page**: [MuslimQoL v0.1.0-rc1 Release](https://github.com/steven1n/muslimqol/releases/tag/v0.1.0-rc1)
- **Primary Binary**: `muslimqol-0.1.0-rc1.jar`
- **SHA-256**: `ecbc02b57b17e9348f102399a034371f97cb6782239b315e29527feaa849acd2`

Place `muslimqol-0.1.0-rc1.jar` into your Minecraft instance's `mods/` directory.

---

## 3. What Testers Should Test

Please focus testing on the following core areas:

1. **Game Startup**: Clean game launch without errors, crashes, or missing dependency warnings.
2. **Singleplayer World Startup**: Creating new worlds and loading existing worlds.
3. **Dedicated Server Startup**: Launching and connecting to a dedicated NeoForge server running MuslimQoL. Verify no client class crashes occur on the server.
4. **Food Tooltips**: Hovering over food items displays colored badges (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`), explanatory reasons, and consumption policy info.
5. **Inventory Food-Status Badges**: Slot decorators render 16x16 pixel-art icons at the bottom-right corner of food slots across various inventory containers (player inventory, chests, crafting tables).
6. **Pork Consumption Blocking**: In `BLOCK` mode (default for restricted foods), attempting to eat pork chops, cooked pork chops, or suspicious stew yields a chat warning, sound effect, and action cancellation.
7. **WARN Behavior**: In `WARN` mode, consuming doubtful/restricted foods displays a warning message and sound effect while permitting consumption.
8. **ALLOW Behavior**: In `ALLOW` mode, foods can be consumed freely without warnings.
9. **Pig Policies**: Testing `NORMAL`, `NO_NATURAL_SPAWN`, `NO_PORK_DROPS`, and `DISABLED_GAMEPLAY` in `config/muslimqol-common.toml`.
10. **Datapack Reload**: Applying custom datapack tags (`#muslimqol:food/*`) or JSON definitions and running `/reload` dynamically updates classifications without restarting Minecraft.
11. **Modded Food Fallback to UNKNOWN**: Unregistered foods from third-party mods default gracefully to `UNKNOWN` with tooltip notice rather than crashing or guessing.
12. **Arabic UI**: Switching game language to `العربية (العالم)` verifies Arabic tooltip translations and layout integrity.

---

## 4. Important Test Scenarios

We encourage testing across diverse configurations and environments:

- **Fresh Minecraft installation**: Clean profile with only NeoForge and MuslimQoL.
- **Existing world**: Loading worlds generated prior to installing MuslimQoL.
- **Singleplayer**: Standard singleplayer survival and creative gameplay.
- **LAN Multiplayer**: Hosting and joining a local network game.
- **Dedicated Server**: Running on Linux/Windows/macOS headless servers with multiple concurrent players.
- **Different GUI scales**: Auto, Normal, Large, Small to ensure overlay badge rendering does not distort.
- **Different languages**: English (`en_us`), Arabic (`ar_sa`), and other languages (checking English fallback).
- **Third-Party Food Mods**: Testing alongside mods such as Farmer's Delight, Create, Autumnity, etc., to verify fallback behavior and tag overrides.

---

## 5. Reporting Bugs

If you find a bug, crash, or rendering glitch, please open an issue using the [Bug Report Template](https://github.com/steven1n/muslimqol/issues/new?template=bug_report.yml).

Please provide the following details:

- **Minecraft version**: (e.g., `1.21.1`)
- **NeoForge version**: (e.g., `21.1.176`)
- **Java version**: (e.g., `21.0.3`)
- **Operating system**: (e.g., `Windows 11`, `macOS 15`, `Ubuntu 24.04`)
- **MuslimQoL version**: (`0.1.0-rc1`)
- **Other installed mods**: Mod list or modpack name.
- **Steps to reproduce**: Step-by-step description to trigger the issue.
- **Expected behavior**: What should have happened.
- **Actual behavior**: What actually occurred.
- **Logs**: Attach `logs/latest.log` or link a gist/pastebin.
- **Crash report**: Attach `crash-reports/crash-*.txt` if a crash occurred.
- **Screenshots**: Helpful for visual or tooltip bugs.

---

## 6. Food Classification Feedback

For incorrect, disputed, or missing food classifications, please use the [Food Classification Template](https://github.com/steven1n/muslimqol/issues/new?template=food_classification.yml).

Required information:

- **Item display name**: (e.g., `Cooked Salmon`)
- **Item registry ID**: (e.g., `minecraft:cooked_salmon`)
- **Source mod**: (`minecraft` or mod name)
- **Current classification**: (`HALAL`, `RESTRICTED`, `DOUBTFUL`, or `UNKNOWN`)
- **Suggested classification**: Proposed state
- **Reason**: Clear explanation for the classification
- **Relevant source/reference**: Classical scholarly consensus, dietary rulings, or factual basis (if applicable)

> [!NOTE]
> **Classification Policy**: When evidence or ingredient composition is uncertain, **`UNKNOWN`** is strictly preferred over guessing. MuslimQoL aims to provide factual, transparent dietary status indicators rather than definitive religious fatwas.
