# EMI Compatibility Smoke Test & Verification Report

This document records the verification results for optional **EMI (dev.emi:emi-neoforge)** compatibility
with MuslimQoL on Minecraft 1.21.1 / NeoForge.

---

## 1. Test Environment & Baseline

* **Minecraft Version**: `1.21.1`
* **NeoForge Baseline**: `21.1.176` (Standalone MuslimQoL) / `21.1.219` (Farmer's Delight compatibility test)
* **EMI Version Tested**: `1.1.24+1.21.1` (`dev.emi:emi-neoforge:1.1.24+1.21.1`)
* **Java Version**: `Java 21` (Eclipse Adoptium OpenJDK 21.0.11)
* **Operating System**: macOS (Darwin 26.7 / x86_64)

---

## 2. Compatibility Verification Matrix

| Area | Check | Evidence Label | Notes & Details |
| :--- | :--- | :---: | :--- |
| **Bytecode / Architecture** | `ItemEmiStack` calls standard tooltip | `STATIC REVIEW PASS` | `ItemEmiStack.getTooltip()` invokes `EmiAgnos.getItemTooltip()` → `Screen.getTooltipFromItem(mc, stack)` → `ItemStack.getTooltipLines()`. Dispatches NeoForge `ItemTooltipEvent`. |
| **Bytecode / Architecture** | Zero production code dependency | `STATIC REVIEW PASS` | Production codebase contains 0 `dev.emi.*` imports. No custom EMI plugin or adapter needed. |
| **Dependency Isolation** | Absent from `runtimeClasspath` | `AUTOMATED PASS` | `./gradlew dependencies --configuration runtimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Absent from `testRuntimeClasspath` | `AUTOMATED PASS` | `./gradlew dependencies --configuration testRuntimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Absent from server runtime | `AUTOMATED PASS` | `./gradlew dependencies --configuration serverAdditionalRuntimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Present in client dev runtime | `AUTOMATED PASS` | `clientAdditionalRuntimeClasspath` correctly resolves `dev.emi:emi-neoforge:1.1.24+1.21.1`. |
| **Client Startup** | Client boot with EMI | `CLIENT RUNTIME PASS` | Client boots to main menu with `EMI 1.1.24+1.21.1+neoforge`, `MuslimQoL 0.1.0-rc1`, and `NeoForge 21.1.176` in ModDiscoverer list. 0 errors. |
| **Tooltip: Apple** | `minecraft:apple` → HALAL | `CLIENT RUNTIME PASS` | Displays `✓ Halal`, reason `Plant-based ingredient`. Exactly one classification block. |
| **Tooltip: Porkchop** | `minecraft:porkchop` → RESTRICTED | `CLIENT RUNTIME PASS` | Displays `⛔ Restricted`, reason `Swine or swine derivative`, policy line `Blocked by MuslimQoL dietary policy`. |
| **Tooltip: Beef** | `minecraft:beef` → UNKNOWN | `CLIENT RUNTIME PASS` | Displays `? Unknown`, reason `Unspecified meat source`. |
| **Tooltip: Stone** | `minecraft:stone` → Non-food | `CLIENT RUNTIME PASS` | Non-edible block item. `FoodClassificationTooltipFormatter.shouldShowTooltip()` returns `false`. Zero classification lines added. |
| **Context: EMI Index** | Sidebar / index grid tooltip | `CLIENT RUNTIME PASS` | Every hovered item in index grid uses `ItemEmiStack.getTooltip()` and inherits `ItemTooltipEvent` lines. |
| **Context: Recipe Slots** | Recipe inputs and outputs | `CLIENT RUNTIME PASS` | Recipe input and output slots in crafting and cooking screens wrap items in `ItemEmiStack`, inheriting classification lines. |
| **Context: Favorites** | Pinned / bookmarks list | `CLIENT RUNTIME PASS` | Hovering favorites panel items inherits standard `ItemTooltipEvent` classification lines. |
| **Farmer's Delight + EMI** | `farmersdelight:cabbage` | `CLIENT RUNTIME PASS` | Displays `✓ Halal` (`Plant-based ingredient`). |
| **Farmer's Delight + EMI** | `farmersdelight:bacon` | `CLIENT RUNTIME PASS` | Displays `⛔ Restricted` (`Swine or swine derivative`). |
| **Farmer's Delight + EMI** | `farmersdelight:minced_beef` | `CLIENT RUNTIME PASS` | Displays `? Unknown` (`Unspecified meat source`). |
| **Farmer's Delight + EMI** | `farmersdelight:dumplings` | `CLIENT RUNTIME PASS` | Displays `⚠ Doubtful` (`Uncertain or variable ingredients`). |
| **Farmer's Delight + EMI** | `farmersdelight:squid_ink_pasta` | `CLIENT RUNTIME PASS` | Displays `? Unknown` (`Non-fish seafood policy unspecified`). |
| **Datapack Reload** | Live `/reload` in client | `CLIENT RUNTIME PASS` | When `/reload` runs, classification registry updates atomically; subsequent EMI hovers immediately reflect new status without client restart. |
| **Dedicated Server Safety** | Server run without EMI | `CLIENT RUNTIME PASS` | Dedicated server boots cleanly, loads MuslimQoL, skips missing FD pack, runs `/muslimqol status` and `/muslimqol classify`, and shuts down with 0 EMI references. |
| **Release JAR Hygiene** | No shaded EMI classes | `AUTOMATED PASS` | `jar tf build/libs/*.jar` verifies 0 `dev/emi` classes in release artifact. |
| **Automated Test Suite** | 68 existing tests passing | `AUTOMATED PASS` | Full suite (68 tests across 9 test classes) passes cleanly on baseline `21.1.176` and FD `21.1.219`. |

---

## 3. Key Findings

1. **Native Inheritance**: EMI 1.21.1 on NeoForge delegates item tooltip creation to `Screen.getTooltipFromItem(mc, stack)`, which in turn invokes `ItemStack.getTooltipLines(...)`. This automatically dispatches NeoForge's `ItemTooltipEvent`.
2. **Zero Code Integration**: Because MuslimQoL's existing `FoodTooltipHandler` already hooks `ItemTooltipEvent`, EMI inherits all dietary classification lines with zero API coupling, zero plugins, and zero custom adapter classes.
3. **No Duplicate Tooltips**: Tooltip lines are appended cleanly to `event.getToolTip()`, appearing exactly once per item hover across all EMI views.
