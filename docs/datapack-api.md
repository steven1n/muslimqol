# Datapack API & Tag Integration

MuslimQoL is designed to be 100% data-driven. Third-party mod developers and modpack creators can classify custom foods and drinks without writing any Java code.

---

## 1. Item Tags

Item tags provide the easiest way to classify foods.

### Available Tags
- `#muslimqol:food/halal`
- `#muslimqol:food/restricted`
- `#muslimqol:food/doubtful`

### File Location
Place tag files inside your datapack at:
```text
data/<namespace>/tags/item/food/<tag_name>.json
```

### Example: Classifying Custom Mod Items

`data/mypack/tags/item/food/restricted.json`:
```json
{
  "replace": false,
  "values": [
    "farmersdelight:bacon",
    "farmersdelight:ham",
    "farmersdelight:pork_sandwich"
  ]
}
```

`data/mypack/tags/item/food/halal.json`:
```json
{
  "replace": false,
  "values": [
    "farmersdelight:fruit_salad",
    "farmersdelight:cabbage",
    "farmersdelight:tomato",
    "farmersdelight:onion"
  ]
}
```

---

## 2. Datapack JSON Files (Detailed Metadata)

If you wish to provide explicit classification reasons that show up in the tooltip, use MuslimQoL JSON classification files.

### File Location
```text
data/<namespace>/muslimqol/food_classifications/<filename>.json
```

### Single Item Schema
```json
{
  "item": "examplemod:date_fruit",
  "status": "HALAL",
  "reason": "plant_based"
}
```

### Multi-Item Array Schema
```json
{
  "values": [
    {
      "item": "examplemod:pork_pie",
      "status": "RESTRICTED",
      "reason": "swine"
    },
    {
      "item": "examplemod:apple_pie",
      "status": "HALAL",
      "reason": "plant_based"
    }
  ]
}
```

### Supported Status Values
- `HALAL`
- `RESTRICTED`
- `DOUBTFUL`
- `UNKNOWN`

### Multi-Datapack Candidate Preservation
When multiple installed datapacks or rule files provide classifications for the same item:
- Both classifications are preserved as candidates rather than being discarded by "last-write-wins" overwriting.
- If both specify the same status, resolution succeeds cleanly without conflict.
- If they specify differing statuses, MuslimQoL marks a conflict, retains all candidates in diagnostics (`/muslimqol classify <item>`), and breaks ties deterministically:
  1. Priority descending (`priority DESC`)
  2. Provider ID ascending (`providerId ASC`)
  3. Rule ID ascending (`ruleId ASC`)
  4. Final stable tie-breaker: `status name ASC`, followed by `reason ASC`.

---

## 3. Optional Compatibility Metadata (v0.2+)

Compatibility datapacks targeting external mods can declare an optional metadata descriptor at:
```text
data/<namespace>/muslimqol/compatibility.json
```

### Schema

```json
{
  "format": 1,
  "name": "Farmer's Delight Compatibility",
  "target_mod": "farmersdelight"
}
```

### Metadata Parse Behavior
- **Absent (No descriptor)**: The datapack is treated as a standard v0.1 legacy pack and loads unconditionally.
- **Valid (`format: 1`)**:
  - If `target_mod` is installed: Active and loaded.
  - If `target_mod` is missing: Safely skipped at reload time with an informational log.
- **Invalid (`format: 999`, unsupported version, or malformed JSON)**:
  - MuslimQoL logs a warning and **skips all food classifications from that namespace**.
  - Invalid metadata never falls back to legacy loading.

---

## 4. Reloading Datapacks & Concurrency

To reload datapack classifications live in-game:
```text
/reload
```
or specifically reload MuslimQoL overrides with:
```text
/muslimqol reload
```

### Transactional Reload Guarantee
Reloading uses transactional reference swapping via `ClassificationRuntimeState`. All datapack rules, user overrides, and compatibility metadata are parsed and assembled completely into immutable structures in memory before being swapped in a single atomic step. Game loop ticks, consumption events, and player tooltip queries never encounter partial, cleared, or hybrid intermediate states.


