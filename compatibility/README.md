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

## 3. Planned First-Party Compatibility Packs (v0.2)

- **Farmer's Delight**: Full tagging for knives, cuttings, cuts, and multi-ingredient stews.
- **Alex's Mobs**: Tagging for exotic animal meats and drops.
- **Aquaculture 2**: Classification for all ocean and freshwater fish species.
- **Pam's HarvestCraft**: Comprehensive ingredient analysis.
