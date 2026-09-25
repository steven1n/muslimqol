# MuslimQoL JEI Compatibility Smoke Test Matrix

This document records the verification results of MuslimQoL's JEI compatibility for Minecraft 1.21.1 / NeoForge 21.1.x.

> **Compatibility mechanism**: MuslimQoL uses NeoForge's standard `ItemTooltipEvent`. JEI 1.21.1 collects
> `ItemStack` tooltip lines by calling `ItemStack.getTooltipLines()`, which fires NeoForge's native
> `ItemTooltipEvent`. MuslimQoL's classification tooltips therefore appear in JEI automatically —
> no JEI-specific callback or plugin is needed. MuslimQoL contains zero `mezz.jei.*` references in
> production Java code.

---

## Environment

| Component | Version | Notes |
| :--- | :--- | :--- |
| **Minecraft** | `1.21.1` | Release target |
| **NeoForge** | `21.1.176` | MDK runtime |
| **Java** | `21.0.11` (Eclipse Adoptium) | JDK 21 |
| **JEI (dev runtime)** | `19.39.0.372` | `mezz.jei:jei-1.21.1-neoforge:19.39.0.372` via `clientAdditionalRuntimeClasspath` |
| **MuslimQoL** | `0.2-dev` (`feature/jei-integration`) | Built on `adae91b` base |

---

## Verification Classification Key

| Label | Meaning |
| :--- | :--- |
| **AUTOMATED PASS** | JUnit test executed in CI/local Gradle test run, deterministic result. |
| **DEDICATED SERVER RUNTIME PASS** | Verified by running `./gradlew runServer --no-daemon` and inspecting logs. |
| **CLIENT BOOT PASS** | Verified from Gradle/NeoForge startup logs showing successful mod loading. |
| **STATIC REVIEW PASS** | Confirmed by code/log inspection without live GUI interaction. |
| **NOT MANUALLY TESTED** | No human hovering in a running graphical client — display untested. |

---

## Test Results

### 1. Automated Unit & Integration Tests

| Test | Expected Result | Status | Test Class |
| :--- | :--- | :---: | :--- |
| `testStatusColorHalal` | GREEN | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testStatusColorRestricted` | RED | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testStatusColorDoubtful` | GOLD | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testStatusColorUnknown` | GRAY | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testStatusColorNull` | GRAY | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testTooltipFormatterMapsHalalCorrectly` | Status line = `food_status.muslimqol.halal` | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testTooltipFormatterMapsRestrictedCorrectly` | Status + policy line present | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testTooltipFormatterMapsDoubtfulCorrectly` | Status + policy line present | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testTooltipFormatterMapsUnknownCorrectly` | Status line = `food_status.muslimqol.unknown` | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testNonFoodDoesNotGenerateTooltipClutter` | Empty list for `minecraft:stone` | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testNullOrEmptyInputProducesNoTooltip` | Empty/false for null/empty input | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |
| `testTooltipReflectsLiveClassificationWithoutStaleCaching` | Override HALAL→DOUBTFUL reflects immediately | **AUTOMATED PASS** | `FoodTooltipCompatibilityTest` |

---

### 2. Runtime Contexts

| Context | Expected Behavior | Status | Method |
| :--- | :--- | :---: | :--- |
| **Dedicated Server (without JEI)** | Server boots, MuslimQoL loads, 0 JEI classes, `/muslimqol` commands work | **DEDICATED SERVER RUNTIME PASS** | `./gradlew runServer --no-daemon` |
| **Release JAR inspection** | Zero `mezz/jei` or `dev/emi` classes packaged | **AUTOMATED PASS** | `jar tf build/libs/*.jar \| grep -E 'mezz/jei\|dev/emi'` |
| **Production Java code inspection** | Zero `mezz.jei.*` imports in `src/main/java` | **STATIC REVIEW PASS** | `grep -rn "mezz.jei" src/main/java` |
| **Test classpath isolation** | JEI absent from `testRuntimeClasspath` | **STATIC REVIEW PASS** | `./gradlew dependencies --configuration testRuntimeClasspath` |
| **Client boot with JEI (dev runtime)** | NeoForge mod loading logs confirm both JEI and MuslimQoL load, no errors | **CLIENT BOOT PASS** | `./gradlew runClient` (logs inspected; headless env — no GUI) |

---

### 3. Dynamic Datapack Reload

| Scenario | Expected Behavior | Status | Method |
| :--- | :--- | :---: | :--- |
| **Live classification override** | Override `minecraft:apple` to `DOUBTFUL`; tooltip reflects change immediately, no stale cache | **AUTOMATED PASS** | `testTooltipReflectsLiveClassificationWithoutStaleCaching` |
| **Conflict resolution** | Deterministic winner displayed; no debug dump in tooltip | **AUTOMATED PASS** | `CompatibilityFrameworkTest` |

---

### 4. Interactive GUI & Localization (Manual Client Checklist)

> These items require a human tester hovering items in a running graphical Minecraft client.
> This development environment is headless — physical monitor is absent, so `glfwGetPrimaryMonitor` fails.
> All items below remain untested until manual QA is performed.

| Test Item | Language | Resolution / Scale | Target Element | Status |
| :--- | :---: | :---: | :--- | :---: |
| `minecraft:apple` | `en_us` | GUI Scale Auto | JEI Ingredient List hover | **NOT MANUALLY TESTED** |
| `minecraft:porkchop` | `en_us` | GUI Scale 2 | JEI Ingredient List hover | **NOT MANUALLY TESTED** |
| `minecraft:beef` | `en_us` | GUI Scale 3 | JEI Recipe Input Slot hover | **NOT MANUALLY TESTED** |
| `minecraft:stone` | `en_us` | GUI Scale 4 | JEI Search Results (no tooltip clutter) | **NOT MANUALLY TESTED** |
| `minecraft:apple` | `ar_sa` | GUI Scale Auto | JEI Ingredient List hover | **NOT MANUALLY TESTED** |
| `minecraft:porkchop` | `ar_sa` | GUI Scale Auto | JEI Recipe Output Slot hover | **NOT MANUALLY TESTED** |
| Duplicate tooltip check | `en_us` | Any | Confirm single classification block | **NOT MANUALLY TESTED** |

> **Code-path confidence**: NeoForge's `ItemStack.getTooltipLines()` dispatches `ItemTooltipEvent`
> before JEI renders the list. MuslimQoL's handler appends classification lines. Static analysis
> of JEI `19.39.0.372` bytecode confirms this pathway. A STATIC REVIEW PASS has been recorded
> for the tooltip pipeline, but human visual verification is still required to confirm rendering.
