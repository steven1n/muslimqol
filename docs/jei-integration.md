# JEI (Just Enough Items) Integration

MuslimQoL provides clean, optional integration with **Just Enough Items (JEI)** on Minecraft 1.21.1 / NeoForge.

---

## 1. Core Principles

1. **Strictly Optional**: MuslimQoL does not require JEI to run, build, or initialize. If JEI is absent, MuslimQoL runs with zero errors or warnings.
2. **Zero Bundling**: JEI classes are never embedded or shaded into the MuslimQoL release JAR.
3. **Single Source of Truth**: The integration queries MuslimQoL's core classification framework (`FoodClassifier.classify(...)`). It never maintains duplicate classification tables or independent precedence rules.
4. **Physical Side Isolation**: All JEI-related code is placed strictly in the client-only package `io.github.muslimqol.client.compat.jei` and is never referenced by common or dedicated-server initialization routines.
5. **No Duplicate Tooltips**: In NeoForge 1.21.1, JEI's ingredient rendering pipeline delegates to `ItemStack.getTooltipLines`, which automatically fires NeoForge's `ItemTooltipEvent`. MuslimQoL leverages this standard pipeline to display food classifications without registering redundant JEI tooltip callbacks.

---

## 2. Dependency Architecture

### Gradle Setup

MuslimQoL uses the standard NeoForge optional dependency model:

```groovy
repositories {
    maven {
        name = "BlameJared"
        url = "https://maven.blamejared.com"
    }
}

dependencies {
    // Compile-time only API dependencies for development
    compileOnly "mezz.jei:jei-${minecraft_version}-common-api:${jei_version}"
    compileOnly "mezz.jei:jei-${minecraft_version}-neoforge-api:${jei_version}"

    // Local client development runtime
    localRuntime "mezz.jei:jei-${minecraft_version}-neoforge:${jei_version}"
}
```

In `gradle.properties`:
```properties
jei_version=19.39.0.372
```

> [!NOTE]
> **Chosen JEI Version: `19.39.0.372`**  
> JEI releases `19.42.0.379` and later require NeoForge `21.1.238+`. Version `19.39.0.372` is the latest release that simultaneously publishes `common-api`, `neoforge-api`, and `neoforge` while supporting our project baseline of NeoForge `21.1.176`.

### Mod Metadata (`neoforge.mods.toml`)

JEI is declared as an optional client dependency:

```toml
[[dependencies.muslimqol]]
modId="jei"
type="optional"
versionRange="[19.0.0,)"
ordering="AFTER"
side="CLIENT"
```

If JEI is present, MuslimQoL loads after JEI on the client. If absent, NeoForge ignores the dependency and proceeds normally.

---

## 3. What Players See in JEI

When hovering over food items in the JEI ingredient list, bookmarks, or recipe slots (inputs/outputs):

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

### Unclassified Meats (e.g. `minecraft:beef` under default conservative policy)
```text
Raw Beef
minecraft:beef

MuslimQoL: Unknown
Unclassified
```

### Non-Food Items (e.g. `minecraft:stone`)
Non-food items produce **zero MuslimQoL lines**, preventing visual clutter across the JEI item grid.

> [!IMPORTANT]
> **No Debugger Dumps**: Player-facing JEI tooltips do not expose internal diagnostic metadata such as `providerId`, `priority`, or `ruleId`. Those remain accessible to admins and pack creators via the `/muslimqol classify <item>` command.

---

## 4. Datapack Live Reloading

Because tooltips are generated dynamically on hover from `FoodClassifier.classify(stack)`:
1. A player or server administrator can load or edit a classification datapack.
2. Running `/reload` swaps the active immutable `ClassificationRuntimeState` atomically.
3. The very next hover in JEI immediately reflects the newly active classification.
4. **No client restart** is required for classification updates to appear in JEI.

---

## 5. Conflict Resolution in JEI

If competing datapacks register conflicting classifications for a food item (e.g. Pack A says `HALAL`, Pack B says `RESTRICTED`):
- JEI displays the **deterministic winner** chosen by MuslimQoL's priority cascade.
- JEI does not flood the player's tooltip with internal candidate conflict dumps.
- Full provenance and all candidate rules can be inspected via `/muslimqol classify <item>`.

---

## 6. Server & Absence Safety

- **Dedicated Server**: The dedicated server never loads `mezz.jei.*` or `io.github.muslimqol.client.compat.jei.*`. Verified with `./gradlew runServer --no-daemon`.
- **Vanilla Client (Without JEI)**: When JEI is absent from the client, standard Minecraft inventory tooltips and hotbar overlay icons continue functioning normally without `NoClassDefFoundError` or `ClassNotFoundException`.
