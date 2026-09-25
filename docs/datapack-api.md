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

- `format`: Schema version (currently `1`).
- `name`: Human-readable name for diagnostics (`/muslimqol providers`).
- `target_mod`: Optional mod ID. If specified, MuslimQoL checks if the target mod is present; if absent, the compatibility pack is safely skipped at load time without error.

---

## 4. Reloading Datapacks

To reload datapack classifications live in-game:
```text
/reload
```
or specifically reload MuslimQoL overrides with:
```text
/muslimqol reload
```

