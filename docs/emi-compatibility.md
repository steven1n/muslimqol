# EMI (Item and Recipe Viewer) Compatibility

MuslimQoL is fully compatible with **EMI** (`dev.emi:emi-neoforge`) on Minecraft 1.21.1 / NeoForge without
any EMI-specific code or plugins in MuslimQoL's production codebase.

---

## 1. Core Principles

1. **Zero EMI API References**: MuslimQoL contains zero `dev.emi.*` imports in its production Java
   code. EMI compatibility is achieved entirely through the standard Minecraft/NeoForge tooltip pipeline.
2. **Strictly Optional**: MuslimQoL does not require EMI to run, build, or initialize. If EMI is
   absent, MuslimQoL runs with zero errors or warnings.
3. **Zero Bundling**: EMI classes are never embedded or shaded into the MuslimQoL release JAR.
4. **Single Source of Truth**: All classification data comes from `FoodClassifier.classify(...)`.
   MuslimQoL never maintains a separate or duplicate classification system for EMI.
5. **Physical Side Isolation**: EMI is declared as a client-only development runtime dependency
   and is never present on the server or test runtime classpath.

---

## 2. How the Compatibility Works

EMI 1.21.1 represents items in all UI contexts using `ItemEmiStack` (`dev.emi.emi.api.stack.ItemEmiStack`).
When constructing tooltips for rendering, `ItemEmiStack.getTooltip()` executes:

```
Player hovers item in EMI (index, recipe slot, favorites)
   ↓
ItemEmiStack.getTooltip()
   ↓
EmiAgnos.getItemTooltip(ItemStack)
   ↓
Screen.getTooltipFromItem(Minecraft.getInstance(), itemStack)
   ↓
ItemStack.getTooltipLines(...)
   ↓
NeoForge dispatches ItemTooltipEvent
   ↓
FoodTooltipHandler.onItemTooltip()
   ↓
FoodClassificationTooltipFormatter.formatTooltip(stack)
   ↓
FoodClassifier.classify(stack)   ← single source of truth
   ↓
Classification components appended to event.getToolTip()
   ↓
ClientHooks.gatherTooltipComponents(...)
   ↓
EMI renders tooltip with MuslimQoL classification lines
```

Because EMI queries tooltips through `Screen.getTooltipFromItem(...)`, NeoForge's native
`ItemTooltipEvent` fires automatically. MuslimQoL's `FoodTooltipHandler` receives this event and
appends the dietary classification lines (status, reason, policy).

No separate EMI plugin (`EmiPlugin`), tooltip adapter, or EMI recipe wrapper is needed.

---

## 3. UI Contexts Supported

All item stack display contexts in EMI natively inherit MuslimQoL tooltips:

* **EMI Sidebar / Index**: The searchable ingredient grid on the right side of screens.
* **Recipe Inputs & Outputs**: Crafting recipes, cooking pot recipes, furnace recipes, cutting board recipes, etc.
* **Favorites / Bookmarks**: Pinned item stacks on the left side of screens.
* **Non-Food Items**: Items that are not edible (e.g. `minecraft:stone`) return `false` from
  `FoodClassificationTooltipFormatter.shouldShowTooltip(stack)` and display zero MuslimQoL lines.
* **Single Block Display**: The classification block is appended exactly once without duplicates.

---

## 4. Dynamic Datapack Reloading

When server or singleplayer datapacks are reloaded via `/reload`:
1. `FoodClassificationReloadListener` updates active classifications atomically.
2. The next time the player hovers over an item in EMI, `ItemEmiStack.getTooltip()` invokes the standard tooltip pipeline.
3. `FoodClassifier.classify(...)` returns the newly applied classification immediately.
4. No client restart or EMI cache rebuilding is required.

---

## 5. Third-Party Compatibility Packs (Farmer's Delight)

Turnkey compatibility packs (such as `muslimqol_farmersdelight`) seamlessly integrate with EMI:
* `farmersdelight:cabbage` → **HALAL** (`plant_based`)
* `farmersdelight:bacon` → **RESTRICTED** (`swine`)
* `farmersdelight:minced_beef` → **UNKNOWN** (`unspecified_meat`)
* `farmersdelight:dumplings` → **DOUBTFUL** (`unknown_ingredients`)
* `farmersdelight:squid_ink_pasta` → **UNKNOWN** (`seafood_policy_unspecified`)

When Farmer's Delight is absent, compatibility definitions safely skip, leaving no phantom items in EMI.

---

## 6. Dependency Architecture

### Gradle Setup

EMI is added as a development-only client runtime dependency:

```groovy
repositories {
    maven {
        name = "Sleeping Town"
        url = "https://repo.sleeping.town/"
    }
}

dependencies {
    // EMI: client-only development runtime dependency for local compatibility testing.
    // EMI is NOT compiled against, NOT bundled, and NOT present on the server or test runtime.
    clientAdditionalRuntimeClasspath "dev.emi:emi-neoforge:${emi_version}"
}
```

In `gradle.properties`:
```properties
emi_version=1.1.24+1.21.1
```

### Dev Staging & Legacy Classpath Filtering

In ModDevGradle 2.x, dependencies on `clientAdditionalRuntimeClasspath` are placed on the Java module
path via `clientLegacyClasspath.txt`. To avoid JPMS duplicate module resolution conflicts
between the legacy classpath (`emi.neoforge._1._21._1`) and NeoForge's `FolderLocator` mod module (`emi`),
`build.gradle` automatically stages the mod in `run/mods/` for client runs and filters the entry from
`clientLegacyClasspath.txt`.

### No Mod Metadata Dependency

No EMI dependency is declared in `META-INF/neoforge.mods.toml`. MuslimQoL and EMI are completely
independent mods.
