# Food Classification Engine

The food classification system categorizes food items into four well-defined states based on Islamic dietary observance guidelines applied to Minecraft mechanics.

---

## Important Notice on Terminology

> **Disclaimer**: MuslimQoL is a personal accessibility and observance tool. It does **not** claim to provide official religious certification or canonical rulings. UI terminology reflects gameplay classifications to help players configure their preferred experience.

---

## The Four Classification States

| Status | Symbol | Default Action | Description |
| :--- | :---: | :---: | :--- |
| **HALAL** | `✓` | **ALLOW** | Plant-based, honey, and seafood items permitted by default. |
| **RESTRICTED** | `⛔` | **BLOCK** | Swine derivatives, carrion, and toxic substances. |
| **DOUBTFUL** | `⚠` | **WARN** | Items with uncertain or contaminated preparations (e.g. suspicious stew, toxic potato). |
| **UNKNOWN** | `?` | **ALLOW** | Food whose provenance cannot be determined automatically. |

---

## Meat Provenance Policy

In Islamic dietary law, land animal meat requires permissible animal species and proper slaughter (Dhabihah). 

Because Minecraft does not track slaughter mechanics in vanilla:
- **Pork** is categorically `RESTRICTED`.
- **Fish & Seafood** are `HALAL`.
- **Land Meats (Beef, Chicken, Mutton, Rabbit)** are classified as **UNKNOWN** by default.

Players and modpack authors can configure meat policies or install slaughter compatibility packs to promote meats to `HALAL`.

---

## Default Built-in Classification Table

### Halal Items
- `minecraft:apple`, `golden_apple`, `enchanted_golden_apple`
- `minecraft:carrot`, `golden_carrot`
- `minecraft:potato`, `baked_potato`
- `minecraft:bread`
- `minecraft:melon_slice`
- `minecraft:sweet_berries`, `glow_berries`
- `minecraft:chorus_fruit`
- `minecraft:beetroot`, `beetroot_soup`
- `minecraft:dried_kelp`
- `minecraft:cookie`, `pumpkin_pie`, `mushroom_stew`
- `minecraft:honey_bottle`
- `minecraft:cod`, `cooked_cod`
- `minecraft:salmon`, `cooked_salmon`
- `minecraft:tropical_fish`

### Restricted Items
- `minecraft:porkchop`
- `minecraft:cooked_porkchop`
- `minecraft:rotten_flesh`
- `minecraft:pufferfish`
- `minecraft:spider_eye`

### Doubtful Items
- `minecraft:poisonous_potato`
- `minecraft:suspicious_stew`

### Unknown Items (Default Fallback)
- `minecraft:beef`, `cooked_beef`
- `minecraft:chicken`, `cooked_chicken`
- `minecraft:mutton`, `cooked_mutton`
- `minecraft:rabbit`, `cooked_rabbit`, `rabbit_stew`
- All third-party mod foods unless tagged or overridden
