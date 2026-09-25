# JEI (Just Enough Items) Compatibility

MuslimQoL is compatible with **Just Enough Items (JEI)** on Minecraft 1.21.1 / NeoForge without
any JEI-specific code in MuslimQoL's production codebase.

---

## 1. Core Principles

1. **Zero JEI API References**: MuslimQoL contains no `mezz.jei.*` imports in its production Java
   code. JEI compatibility is achieved entirely through the standard Minecraft/NeoForge tooltip pipeline.
2. **Strictly Optional**: MuslimQoL does not require JEI to run, build, or initialize. If JEI is
   absent, MuslimQoL runs with zero errors or warnings.
3. **Zero Bundling**: JEI classes are never embedded or shaded into the MuslimQoL release JAR.
4. **Single Source of Truth**: All classification data comes from `FoodClassifier.classify(...)`.
   MuslimQoL never maintains a duplicate classification system for JEI.
5. **Physical Side Isolation**: JEI is declared as a `clientAdditionalRuntimeClasspath` dependency
   only, and is never present on the server or test runtime classpath.

---

## 2. How the Compatibility Works

JEI 1.21.1 collects ingredient tooltip lines by calling `ItemStack.getTooltipLines()`, which
dispatches NeoForge's native `ItemTooltipEvent`. MuslimQoL registers a handler for this event in
`FoodTooltipHandler`, so the food classification status (Halal / Restricted / Doubtful / Unknown)
appears automatically in JEI's ingredient list, bookmarks, and recipe views.

```
Player hovers item in JEI
   ↓
JEI calls ItemStack.getTooltipLines()
   ↓
NeoForge dispatches ItemTooltipEvent
   ↓
FoodTooltipHandler.onItemTooltip()
   ↓
FoodClassificationTooltipFormatter.formatTooltip(stack)
   ↓
FoodClassifier.classify(stack)   ← single source of truth
   ↓
Lines appended to tooltip
   ↓
JEI renders tooltip with MuslimQoL classification lines
```

No separate JEI plugin, JEI tooltip provider, or JEI ingredient renderer is used or needed.

---

## 3. Dependency Architecture

### Gradle Setup

JEI is declared as a `clientAdditionalRuntimeClasspath` dependency (ModDevGradle 2.x DSL):

```groovy
dependencies {
    // Client-only development runtime. JEI is absent from runtimeClasspath,
    // serverAdditionalRuntimeClasspath, and testRuntimeClasspath.
    clientAdditionalRuntimeClasspath "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"
}
```

In `gradle.properties`:
```properties
jei_version=19.39.0.372
```

> [!NOTE]
> **Tested Development JEI Version: `19.39.0.372`**
> JEI releases `19.42.0.379` and later require NeoForge `21.1.238+`. Version `19.39.0.372` is the
> latest release that supports our project baseline of NeoForge `21.1.176`. Only this specific
> version has been tested as a local development runtime. Compatibility with other `19.x` builds
> is not guaranteed.

### No Mod Metadata Dependency

Because MuslimQoL does not load or call JEI APIs, no JEI dependency is declared in
`META-INF/neoforge.mods.toml`. MuslimQoL and JEI are independent mods whose tooltip pipelines
happen to interoperate through the standard NeoForge event system.

---

## 4. What Players See in JEI

When hovering over food items in the JEI ingredient list, bookmarks, or recipe slots:

### Permitted Foods (e.g. `minecraft:apple`)
```text
Apple
minecraft:apple

MuslimQoL: Halal
Plant-based ingredient
```

### Restricted Foods (e.g. `minecraft:porkchop`)
```text
Raw Porkchop
minecraft:porkchop

MuslimQoL: Restricted
Derived from swine / pig

Policy: Block
```

### Unclassified Meats (e.g. `minecraft:beef`)
```text
Raw Beef
minecraft:beef

MuslimQoL: Unknown
Unclassified
```

### Non-Food Items (e.g. `minecraft:stone`)
Non-food items produce **zero MuslimQoL lines**, preventing visual clutter across the JEI item grid.

> [!IMPORTANT]
> **No Debugger Dumps**: Player-facing tooltips do not expose internal diagnostic metadata such as
> `providerId`, `priority`, or `ruleId`. Those remain accessible to admins via
> `/muslimqol classify <item>`.

---

## 5. Datapack Live Reloading

Because tooltips are generated dynamically on hover from `FoodClassifier.classify(stack)`:
1. A player or server administrator edits a classification datapack.
2. Running `/reload` atomically swaps the active `ClassificationRuntimeState`.
3. The very next hover in JEI immediately reflects the newly active classification.
4. **No client restart** is required for classification updates to appear.

---

## 6. Conflict Resolution in JEI

If competing datapacks register conflicting classifications for a food item:
- JEI displays the **deterministic winner** chosen by MuslimQoL's priority cascade.
- Full provenance and all candidate rules can be inspected via `/muslimqol classify <item>`.

---

## 7. Server & Absence Safety

- **Dedicated Server**: The dedicated server never loads `mezz.jei.*`. JEI is absent from
  `serverAdditionalRuntimeClasspath`. Verified with `./gradlew runServer --no-daemon`.
- **Vanilla Client (Without JEI)**: When JEI is absent from the client, standard inventory
  tooltips and hotbar overlay icons continue functioning normally without `NoClassDefFoundError`
  or `ClassNotFoundException`. MuslimQoL contains no unconditional JEI class references.
- **Test Classpath**: JEI is absent from `testRuntimeClasspath`, ensuring all unit tests
  run without JEI.
