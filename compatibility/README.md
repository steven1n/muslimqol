# Compatibility Guidelines & Third-Party Integration

MuslimQoL is designed from the ground up to coexist cleanly with modded ecosystems, culinary overhauls, and large modpacks.

---

## 1. Zero Registry Mutation Guarantee

A common failure mode in restrictive mods is unregistering vanilla entities or items (such as removing `minecraft:pig`). 

MuslimQoL **never** unregisters:
- `minecraft:pig`
- `minecraft:porkchop`
- `minecraft:cooked_porkchop`

This guarantees that:
- Other mods depending on pig entity registries, loot tables, trades, or advancements never crash.
- Datapacks and craft recipes that reference pork items remain syntactically valid.
- Switching `PigPolicy` back and forth leaves worlds completely uncorrupted.

---

## 2. Integrating Your Mod

If you develop a food mod (e.g. adding new crops, dishes, meats, or beverages), you can make it MuslimQoL-ready in seconds using standard tags:

Add your item identifiers to:
```text
data/muslimqol/tags/item/food/halal.json
data/muslimqol/tags/item/food/restricted.json
data/muslimqol/tags/item/food/doubtful.json
```

Or distribute a custom JSON under:
```text
data/<yourmod>/muslimqol/food_classifications/<item>.json
```

---

## 3. First-Party Compatibility Packs

- **Farmer's Delight**: Full tagging for knives, cuttings, cuts, and multi-ingredient stews.
- **Pam's HarvestCraft 2 Food Core**: Comprehensive ingredient analysis across 180 edible items.

---

## 4. Compatibility Pack Version Safety & Trust States

To prevent silent divergence when third-party mods release updates, compatibility packs support version verification descriptors in `compatibility.json`:

```json
{
  "format": 1,
  "name": "MuslimQoL Pam's HarvestCraft 2 Food Core Compatibility",
  "target_mod": "pamhc2foodcore",
  "target_version": "1.0.4",
  "reference_jar_sha256": "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
}
```

### Runtime Trust States

At startup and resource reload, MuslimQoL determines the trust state of each compatibility pack:

1. **`VERIFIED`**:
   - The target mod is loaded and its installed version exactly matches `target_version`.
   - Packs without a declared `target_version` load as `VERIFIED (legacy unversioned pack)`.
2. **`UNVERIFIED`**:
   - The target mod is loaded and the pack specifies `target_version`, but the installed version differs (or cannot be determined).
   - **Gameplay behavior**: The pack **remains active by default** so gameplay is not disrupted, but a clear diagnostic warning is logged.
   - **Semantic note**: `UNVERIFIED` does **not** mean incompatible. It simply informs players and modpack authors that this specific mod version has not been audited against the pack.
3. **`SKIPPED`**:
   - The target mod is not loaded, or the pack metadata is malformed/invalid. The pack's food classifications are deactivated safely.

### Provenance Metadata Semantics

- **`target_version`**: Evaluated against the runtime mod version resolved via the mod loader.
- **`reference_jar_sha256`**: Audit reference recording the exact artifact used during compatibility auditing. MuslimQoL does **not** hash installed mod JARs at runtime.

