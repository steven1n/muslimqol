# Pam's HarvestCraft 2 Food Core Compatibility Pack Audit & Verification

> [!IMPORTANT]
> **Disclaimer**: MuslimQoL provides in-game gameplay classifications and dietary HUD indicators based on virtual crafting recipes and simulated item ingredients in Minecraft. It is **not** an Islamic jurisprudential authority or a real-world halal certification body.

---

## 1. Executive Summary

This document records the human-reviewed dietary classification audit and verification for **Pam's HarvestCraft 2 - Food Core** (Minecraft 1.21.1, NeoForge).

| Metric | Specification / Result |
| :--- | :--- |
| **Target Mod Name** | Pam's HarvestCraft 2 - Food Core |
| **Target Version** | `1.0.4` |
| **Target Mod ID** | `pamhc2foodcore` |
| **Reference JAR** | `pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar` |
| **JAR SHA-256** | `acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9` |
| **Total Registered Items** | **202** |
| **Player-Edible Items** | **180** |
| **Classified in Pack** | **180** (100% complete coverage) |
| **Pack Validation Status** | **Clean = True** (0 missing, 0 extra, 0 duplicates, 0 unknown IDs) |

---

## 2. Classification Distribution

| Status | Count | Percentage | Primary Reasons | Description |
| :--- | :---: | :---: | :--- | :--- |
| **HALAL** | **125** | 69.4% | `plant_based` (52)<br>`audited_permitted_recipe` (65)<br>`fish` (8) | Pure plant/crop foods, fruits, vegetables, scaled fish, and audited baked goods/dairy/egg dishes. |
| **RESTRICTED** | **13** | 7.2% | `swine` (13) | Items containing mandatory pork or swine-derived ingredients. |
| **DOUBTFUL** | **13** | 7.2% | `variable_provenance` (13) | Dishes utilizing variable stock or ambiguous meat tags with valid pork alternatives. |
| **UNKNOWN** | **29** | 16.1% | `unspecified_meat` (29) | Livestock meat (beef, chicken, mutton, rabbit) with unverified in-game slaughter provenance. |
| **Total** | **180** | 100.0% | — | Exact bidirectional Set equality with reference edible manifest. |

---

## 3. All RESTRICTED Items (13 Items)

Under MuslimQoL policy, items are classified as **RESTRICTED** only when recipe provenance establishes mandatory swine-derived content. Substring matching is never sufficient; each item was verified against actual recipe data.

| Item ID | In-Game Item Name | Verified In-Game Recipe Ingredients | Reason |
| :--- | :--- | :--- | :--- |
| `pamhc2foodcore:baconandeggsitem` | Bacon and Eggs | `#c:tool_skillet`, `#c:egg`, `#c:rawpork` | `swine` |
| `pamhc2foodcore:baconcheeseburgeritem` | Bacon Cheeseburger | `#c:tool_skillet`, `#c:groundmeats/groundbeef`, `#c:condiments`, `#c:bread`, `#c:rawpork`, `#c:cheese` | `swine` |
| `pamhc2foodcore:basicporksandwichitem` | Pork Sandwich | `#c:tool_skillet`, `#c:groundmeats/groundpork` (`groundporkitem` $\to$ `#c:rawpork`), `#c:condiments`, `#c:bread` | `swine` |
| `pamhc2foodcore:chocolatebaconitem` | Chocolate Bacon | `#c:tool_saucepan`, `#c:chocolatebar`, `#c:rawpork` | `swine` |
| `pamhc2foodcore:cookedgroundporkitem` | Cooked Ground Pork | Smelted / Smoked `pamhc2foodcore:groundporkitem` (`#c:rawpork`) | `swine` |
| `pamhc2foodcore:epicbaconitem` | Epic Bacon | `#c:tool_bakeware`, `#c:cookedpork`, dyes | `swine` |
| `pamhc2foodcore:grilledcheeseandhamitem` | Grilled Cheese and Ham | `#c:tool_skillet`, `#c:bread`, `#c:butter`, `#c:cheese`, `#c:rawpork` | `swine` |
| `pamhc2foodcore:grilledporkskeweritem` | Grilled Pork Skewer | `#c:tool_skillet`, `#c:rawpork`, `#c:vegetables`, `#c:rods/wooden` | `swine` |
| `pamhc2foodcore:groundporkitem` | Ground Pork | `#c:tool_grinder`, `#c:rawpork` | `swine` |
| `pamhc2foodcore:hotdogitem` | Hot Dog | `#c:tool_cuttingboard`, `pamhc2foodcore:groundporkitem`, `#c:bread`, `#c:condiments` | `swine` |
| `pamhc2foodcore:porkjerkyitem` | Pork Jerky | `#c:tool_cuttingboard`, `#c:rawpork`, `#c:salt` | `swine` |
| `pamhc2foodcore:porknoodlesoupitem` | Pork Noodle Soup | `#c:tool_pot`, `#c:pasta`, `#c:stock`, `#c:vegetables`, `#c:rawpork` | `swine` |
| `pamhc2foodcore:porkpotpieitem` | Pork Pot Pie | `#c:tool_bakeware`, `#c:rawpork`, `#c:crops/potato`, `#c:crops/carrot`, `#c:dough` | `swine` |

---

## 4. All DOUBTFUL Items (13 Items)

Items are classified as **DOUBTFUL** (`variable_provenance`) when legitimate in-game crafting recipes permit mutually exclusive ingredient paths of differing religious status—specifically where pork is an optional source alongside halal/permissible sources.

In Pam's HarvestCraft 2 Food Core, `#c:stock` is crafted from `#c:stock_ingredients`, which expands to:
$$\text{\#c:stock\_ingredients} \in \{\text{bone}, \text{rawbeef}, \text{rawchicken}, \text{rawcod}, \text{rawmutton}, \mathbf{rawpork}, \text{rawrabbit}, \text{rawsalmon}, \text{rawtropicalfish}\}$$

Because a player can brew stock from permissible fish or unverified beef, OR from restricted pork, all items requiring stock inherit this dietary ambiguity.

| Item ID | In-Game Item Name | Ambiguous Variable Ingredient | Reason |
| :--- | :--- | :--- | :--- |
| `pamhc2foodcore:stockitem` | Stock | `#c:stock_ingredients` (includes `#c:stock_ingredients/rawpork` alongside beef/chicken/fish) | `variable_provenance` |
| `pamhc2foodcore:carrotsoupitem` | Carrot Soup | `#c:stock` (variable stock origin) | `variable_provenance` |
| `pamhc2foodcore:potatosoupitem` | Potato Soup | `#c:stock` (variable stock origin) | `variable_provenance` |
| `pamhc2foodcore:pumpkinsoupitem` | Pumpkin Soup | `#c:stock` (variable stock origin) | `variable_provenance` |
| `pamhc2foodcore:noodlesoupitem` | Noodle Soup | `#c:stock` (variable stock origin) | `variable_provenance` |
| `pamhc2foodcore:vegetablenoodlesoupitem` | Vegetable Noodle Soup | `#c:stock` (variable stock origin) | `variable_provenance` |
| `pamhc2foodcore:beefnoodlesoupitem` | Beef Noodle Soup | `#c:stock` (stock may be pork-derived despite raw beef component) | `variable_provenance` |
| `pamhc2foodcore:chickennoodlesoupitem` | Chicken Noodle Soup | `#c:stock` (stock may be pork-derived despite raw chicken component) | `variable_provenance` |
| `pamhc2foodcore:muttonnoodlesoupitem` | Mutton Noodle Soup | `#c:stock` (stock may be pork-derived despite raw mutton component) | `variable_provenance` |
| `pamhc2foodcore:rabbitnoodlesoupitem` | Rabbit Noodle Soup | `#c:stock` (stock may be pork-derived despite raw rabbit component) | `variable_provenance` |
| `pamhc2foodcore:fishnoodlesoupitem` | Fish Noodle Soup | `#c:stock` (stock may be pork-derived despite raw fish component) | `variable_provenance` |
| `pamhc2foodcore:meatloafitem` | Meatloaf | `#c:groundmeats` (tag includes `groundporkitem` alongside beef/chicken/mutton/rabbit/fish) | `variable_provenance` |
| `pamhc2foodcore:stewitem` | Stew | `#c:rawmeats` (tag includes `#c:rawpork` alongside beef/chicken/mutton/rabbit) | `variable_provenance` |

---

## 5. All UNKNOWN Meat Items (29 Items)

Livestock meats (cattle, poultry, sheep, rabbit) are permissible in principle but require halal slaughter. In unmodded/generic gameplay where slaughter rituals are not simulated, MuslimQoL conservatively marks these items as **UNKNOWN** (`unspecified_meat`).

### Beef Items (6)
- `pamhc2foodcore:groundbeefitem` (Ground Beef)
- `pamhc2foodcore:cookedgroundbeefitem` (Cooked Ground Beef)
- `pamhc2foodcore:beefjerkyitem` (Beef Jerky)
- `pamhc2foodcore:grilledbeefskeweritem` (Grilled Beef Skewer)
- `pamhc2foodcore:beefpotpieitem` (Beef Pot Pie)
- `pamhc2foodcore:potroastitem` (Pot Roast)

### Chicken Items (8)
- `pamhc2foodcore:groundchickenitem` (Ground Chicken)
- `pamhc2foodcore:cookedgroundchickenitem` (Cooked Ground Chicken)
- `pamhc2foodcore:chickenjerkyitem` (Chicken Jerky)
- `pamhc2foodcore:chickennuggetitem` (Chicken Nugget)
- `pamhc2foodcore:chickendinneritem` (Chicken Dinner)
- `pamhc2foodcore:chickenpotpieitem` (Chicken Pot Pie)
- `pamhc2foodcore:friedchickenitem` (Fried Chicken)
- `pamhc2foodcore:grilledchickenskeweritem` (Grilled Chicken Skewer)

### Mutton Items (5)
- `pamhc2foodcore:groundmuttonitem` (Ground Mutton)
- `pamhc2foodcore:cookedgroundmuttonitem` (Cooked Ground Mutton)
- `pamhc2foodcore:muttonjerkyitem` (Mutton Jerky)
- `pamhc2foodcore:grilledmuttonskeweritem` (Grilled Mutton Skewer)
- `pamhc2foodcore:muttonpotpieitem` (Mutton Pot Pie)

### Rabbit Items (5)
- `pamhc2foodcore:groundrabbititem` (Ground Rabbit)
- `pamhc2foodcore:cookedgroundrabbititem` (Cooked Ground Rabbit)
- `pamhc2foodcore:rabbitjerkyitem` (Rabbit Jerky)
- `pamhc2foodcore:grilledrabbitskeweritem` (Grilled Rabbit Skewer)
- `pamhc2foodcore:rabbitpotpieitem` (Rabbit Pot Pie)

### Meat Sandwiches & Burgers (5)
- `pamhc2foodcore:basichamburgeritem` (Hamburger — ground beef)
- `pamhc2foodcore:basiccheeseburgeritem` (Cheeseburger — ground beef + cheese)
- `pamhc2foodcore:basicchickensandwichitem` (Chicken Sandwich — ground chicken)
- `pamhc2foodcore:basicmuttonsandwichitem` (Mutton Sandwich — ground mutton)
- `pamhc2foodcore:basicrabbitsandwichitem` (Rabbit Sandwich — ground rabbit)

---

## 6. All HALAL Items (125 Items)

### 6.1 Scaled Fish (8 Items, Reason: `fish`)
- `pamhc2foodcore:groundfishitem`
- `pamhc2foodcore:cookedgroundfishitem`
- `pamhc2foodcore:fishjerkyitem`
- `pamhc2foodcore:grilledfishskeweritem`
- `pamhc2foodcore:basicfishsandwichitem`
- `pamhc2foodcore:fishandchipsitem`
- `pamhc2foodcore:fishpotpieitem`
- `pamhc2foodcore:fishsticksitem`

### 6.2 Pure Plant-Based Foods (52 Items, Reason: `plant_based`)
- **Juices & Smoothies (12)**: `applejuiceitem`, `carrotjuiceitem`, `chorusjuiceitem`, `glowberryjuiceitem`, `melonjuiceitem`, `p8juiceitem`, `sweetberryjuiceitem`, `fruitpunchitem`, `applesmoothieitem`, `chorussmoothieitem`, `glowberrysmoothieitem`, `melonsmoothieitem`, `sweetberrysmoothieitem`
- **Jellies, Sauces & Salads (7)**: `applejellyitem`, `applesauceitem`, `chorusjellyitem`, `fruitsaladitem`, `glowberryjellyitem`, `melonjellyitem`, `sweetberryjellyitem`
- **Pies & Breads (pure plant dough) (10)**: `applepieitem`, `carrotbreaditem`, `carrotpieitem`, `chocolatepieitem`, `choruspieitem`, `glowberrypieitem`, `honeypieitem`, `melonpieitem`, `pumpkinbreaditem`, `sweetberrypieitem`
- **Snacks, Popsicles & Confections (15)**: `applepopsicleitem`, `choruspopsicleitem`, `glowberrypopsicleitem`, `melonpopsicleitem`, `sweetberrypopsicleitem`, `crackeritem`, `friesitem`, `potatochipsitem`, `softpretzelitem`, `roastedsunflowerseedsitem`, `sunflowerseedsitem`, `trailmixitem`, `cottoncandyitem`, `gummycreepersitem`, `jellybeansitem`, `marshmellowsitem`, `chocolatebaritem`, `smoresitem`
- **Vegetable Dishes (2)**: `bakedvegetablemedlyitem`, `pickledbeetsitem`, `grilledveggieskeweritem`, `veggiepotpieitem`

### 6.3 Audited Permitted Recipes (65 Items, Reason: `audited_permitted_recipe`)
Foods containing dairy (milk, butter, cheese, yogurt, ice cream) or eggs alongside plant ingredients:
- **Egg Dishes (3)**: `boiledeggitem`, `friedeggitem`, `scrambledeggitem`
- **Dairy Products & Desserts (16)**: `cheeseitem`, `yogurtitem`, `icecreamitem`, `caramelitem`, `caramelicecreamitem`, `caramelpieitem`, `chocolateicecreamitem`, `chocolatemilkitem`, `hotchocolateitem`, `fudgesicleitem`, `chocolatecaramelfudgeitem`, `chocolaterollitem`, `cookiesandmilkitem`, `crackersandcheeseitem`, `appleyogurtitem`, `caramelyogurtitem`, `chocolateyogurtitem`, `chorusyogurtitem`, `glowberryyogurtitem`, `melonyogurtitem`, `pumpkinyogurtitem`, `sweetberryyogurtitem`
- **Baked Goods (Donuts, Muffins, Cakes) (34)**: `plaindonutitem`, `appledonutitem`, `carameldonutitem`, `carrotdonutitem`, `chocolatedonutitem`, `chorusdonutitem`, `glowberrydonutitem`, `honeyglazeddonutitem`, `melondonutitem`, `powdereddonutitem`, `pumpkindonutitem`, `sprinklesdonutitem`, `sweetberrydonutitem`, `applemuffinitem`, `caramelmuffinitem`, `carrotmuffinitem`, `chocolatemuffinitem`, `chorusmuffinitem`, `glowberrymuffinitem`, `honeymuffinitem`, `melonmuffinitem`, `pumpkinmuffinitem`, `sweetberrymuffinitem`, `carrotcakeitem`, `cheesecakeitem`, `chocolatecakeitem`, `pumpkincheesecakeitem`
- **Savory Composite Meals (6)**: `macncheeseitem`, `grilledcheeseitem`, `basicveggieburgeritem`, `mashedpotatoesitem`, `butteredbakedpotatoitem`, `glazedcarrotsitem`, `caramelappleitem`
- **Toasts (6)**: `toastitem`, and the 5 jelly toast items:
  - `applejellytoastitem`
  - `chorusjellytoastitem`
  - `glowberryjellytoastitem`
  - `melonjellytoastitem`
  - `sweetberryjellytoastitem`

---

## 7. Deep Review: The 5 Incomplete Jelly Toast Items

At default audit depth 12, the Compatibility Audit Engine flags 5 items as having incomplete provenance due to hitting the depth limit (`DEPTH_LIMIT`):
1. `applejellytoastitem`
2. `chorusjellytoastitem`
3. `glowberryjellytoastitem`
4. `melonjellytoastitem`
5. `sweetberryjellytoastitem`

### Technical Cause of Depth Truncation
The recipe for jelly toast is:
$$\text{cutting board} + \text{toast} + \text{jelly}$$
Tracing toast expands through deep intermediate chains:
$$\text{jelly toast} \to \text{toast} \to \text{bread} \to \text{dough} \to \text{flour} + \text{salt} + \text{water}$$
In Pam's HarvestCraft 2, multi-branch tag expansions for salt, water, and flour exceed 12 graph hops before terminating at leaf ingredients.

### Exhaustive Manual Verification (Depth 16)
Running the engine at `--max-provenance-depth 16` cleanly resolves all 5 items (`incomplete=False`):
- `applejellytoastitem`: Fruit jelly (apple + sugar) + Toast (bread + butter).
- `butteritem`: Crafted from `#c:milk` (cow milk bucket).
- `bread`: Crafted from wheat flour, water, salt.
- Leaf ingredients: `minecraft:apple`, `minecraft:sugar`, `minecraft:milk_bucket`, wheat, water, salt.

### Audit Decision
None of the leaf ingredients contain swine, carrion, blood, or alcohol. Dairy and plant components are verified permissible. Therefore, all 5 items are classified as **HALAL** with reason **`audited_permitted_recipe`**.

---

## 8. Verification Results

### 8.1 Pack Validator Output
```text
Pack Name:    muslimqol_pamhc2foodcore
Clean:        True
Classified:   180
Missing:      0
Extra:        0
Duplicates:   0
Unknown IDs:  0
Diagnostics:  0
Status distribution: {'DOUBTFUL': 13, 'HALAL': 125, 'UNKNOWN': 29, 'RESTRICTED': 13}
```

### 8.2 Test Suite Execution
- **Python Unit Tests**: **73 passed**, 0 failed (`tools/compatibility/tests/`)
- **Java Unit Tests**: **74 passed**, 0 failed (`./gradlew clean test build`)
- **Farmer's Delight Pack Regression**: Clean = True, 89/89 classified (`HALAL: 52, RESTRICTED: 11, DOUBTFUL: 5, UNKNOWN: 21`)
- **Production Java Changes**: **0 lines modified** (`src/main/java` remains completely unchanged)
- **Runtime Optionality**: Pam's HarvestCraft 2 remains an optional datapack; no hard compile-time or runtime dependency exists.
