# EMI Compatibility Smoke Test & Verification Report

This document records the verification results for optional **EMI (dev.emi:emi-neoforge)** compatibility
with MuslimQoL on Minecraft 1.21.1 / NeoForge, clearly separating automated and bytecode-verified evidence
from interactive manual UI testing.

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
| **Bytecode / Architecture** | `ItemEmiStack` calls standard tooltip | `STATIC REVIEW PASS` | Bytecode inspection of `ItemEmiStack.getTooltip()` confirms it invokes `EmiAgnos.getItemTooltip()` → `Screen.getTooltipFromItem(mc, stack)` → `ItemStack.getTooltipLines()`. Dispatches NeoForge `ItemTooltipEvent`. |
| **Bytecode / Architecture** | Zero production code dependency | `STATIC REVIEW PASS` | Production codebase contains 0 `dev.emi.*` imports. No custom EMI plugin or adapter needed. |
| **Dependency Isolation** | Absent from `runtimeClasspath` | `AUTOMATED PASS` | `./gradlew dependencies --configuration runtimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Absent from `testRuntimeClasspath` | `AUTOMATED PASS` | `./gradlew dependencies --configuration testRuntimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Absent from server runtime | `AUTOMATED PASS` | `./gradlew dependencies --configuration serverAdditionalRuntimeClasspath` contains zero references to `dev.emi`. |
| **Dependency Isolation** | Present in client dev runtime | `AUTOMATED PASS` | `clientAdditionalRuntimeClasspath` correctly resolves `dev.emi:emi-neoforge:1.1.24+1.21.1`. |
| **Client Startup** | Client boot with EMI | `CLIENT BOOT PASS` | Client boots cleanly to main menu with `EMI 1.1.24+1.21.1+neoforge`, `MuslimQoL 0.1.0-rc1`, and `NeoForge 21.1.176` in ModDiscoverer list. 0 errors. |
| **Tooltip: Apple** | `minecraft:apple` → HALAL | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Expected `✓ Halal` (`Plant-based ingredient`) from verified `ItemTooltipEvent` pipeline; interactive GUI rendering not manually verified. |
| **Tooltip: Porkchop** | `minecraft:porkchop` → RESTRICTED | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Expected `⛔ Restricted` (`Swine or swine derivative`, policy line `Blocked by MuslimQoL dietary policy`) from verified pipeline; interactive GUI rendering not manually verified. |
| **Tooltip: Beef** | `minecraft:beef` → UNKNOWN | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Expected `? Unknown` (`Unspecified meat source`) from verified pipeline; interactive GUI rendering not manually verified. |
| **Tooltip: Stone** | `minecraft:stone` → Non-food | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Non-edible block item. `FoodClassificationTooltipFormatter.shouldShowTooltip()` returns `false`. Zero classification lines expected; interactive GUI rendering not manually verified. |
| **Context: EMI Index** | Sidebar / index grid tooltip | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Architecturally supported via `ItemEmiStack.getTooltip()`. Visual layout, tooltip clipping, and interactive rendering not manually verified on screen. |
| **Context: Recipe Slots** | Recipe inputs and outputs | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Recipe input and output slots wrap items in `ItemEmiStack`, inheriting standard tooltip dispatch. Interactive GUI rendering not manually verified. |
| **Context: Favorites** | Pinned / bookmarks list | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Favorites items query `ItemEmiStack.getTooltip()`. Interactive GUI rendering not manually verified. |
| **Farmer's Delight + EMI** | `farmersdelight:cabbage` | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Composed path verified (`muslimqol_farmersdelight` classification data + standard tooltip pipeline). Interactive GUI rendering not manually verified. |
| **Farmer's Delight + EMI** | `farmersdelight:bacon` | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Composed path verified. Interactive GUI rendering not manually verified. |
| **Farmer's Delight + EMI** | `farmersdelight:minced_beef` | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Composed path verified. Interactive GUI rendering not manually verified. |
| **Farmer's Delight + EMI** | `farmersdelight:dumplings` | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Composed path verified. Interactive GUI rendering not manually verified. |
| **Farmer's Delight + EMI** | `farmersdelight:squid_ink_pasta` | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | Composed path verified. Interactive GUI rendering not manually verified. |
| **Datapack Reload** | Live `/reload` in client | `STATIC REVIEW PASS`<br>`NOT MANUALLY TESTED` | No MuslimQoL classification cache exists in the EMI compatibility path. Subsequent tooltip queries resolve against the current runtime state. Interactive EMI reload behavior has not yet been manually verified. |
| **Dedicated Server Safety** | Server run without EMI | `DEDICATED SERVER RUNTIME PASS` | Dedicated server boots cleanly, loads MuslimQoL, skips missing FD pack, runs `/muslimqol status` and `/muslimqol classify`, and shuts down cleanly with 0 EMI references in server logs. |
| **Release JAR Hygiene** | No shaded EMI classes | `AUTOMATED PASS` | `jar tf build/libs/*.jar` verifies 0 `dev/emi` classes in release artifact. |
| **Automated Test Suite** | 68 existing tests passing | `AUTOMATED PASS` | Full suite (68 tests across 9 test classes) passes cleanly on baseline `21.1.176` and FD `21.1.219`. |

---

## 3. Key Findings

1. **Native Inheritance**: EMI 1.21.1 on NeoForge delegates item tooltip creation to `Screen.getTooltipFromItem(mc, stack)`, which in turn invokes `ItemStack.getTooltipLines(...)`. This automatically dispatches NeoForge's `ItemTooltipEvent`.
2. **Zero Code Integration**: Because MuslimQoL's existing `FoodTooltipHandler` already hooks `ItemTooltipEvent`, EMI inherits all dietary classification lines with zero API coupling, zero plugins, and zero custom adapter classes.
3. **Evidence Distinction**: Architectural compatibility, client bootstrap, dedicated server safety, dependency isolation, and unit test suites are fully verified. Interactive graphical rendering, tooltip layout, and manual hover behavior remain categorized as `NOT MANUALLY TESTED` until human in-game verification is performed.
