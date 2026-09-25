# Farmer's Delight 1.21.1 Food Classification Audit

> [!NOTE]
> **Disclaimer**: These are conservative gameplay classifications based on in-game recipe composition and are not a substitute for religious guidance or real-world halal certification.

This document details the food classification audit for **Farmer's Delight 1.3.4** on **Minecraft 1.21.1** (NeoForge).

## Audit Scope & Summary

* **Target Mod**: Farmer's Delight (`farmersdelight`)
* **Tested Version**: `1.21.1-1.3.4`
* **Reference JAR SHA-256**: `139ad7696462c89c03eea463f805abffa552526c5dadaadae221dd9624cb197c`
* **Platform**: NeoForge 21.1.x / Java 21
* **Compatibility Namespace**: `muslimqol_farmersdelight`
* **Total Audited Edible Items**: 89
  * Hand-edible items: 80
  * Placeable feasts & whole pies: 9
* **Classification Breakdown**:
  * **HALAL**: 52 (seafood/fish, pure crops, vegetables, and audited permitted recipes containing dairy/eggs)
  * **RESTRICTED**: 11 (swine-derived cuts, bacon meals, pork ramen, pumpkin soup with pork, dog food with carrion)
  * **DOUBTFUL**: 5 (variable protein mixtures like dumplings, cabbage rolls, barbecue sticks, and bone broth)
  * **UNKNOWN**: 21 (ordinary uncertified livestock meats: beef, chicken, mutton dishes, and non-fish seafood items like squid ink pasta)
  * **Unclassified**: 0

---

## Detailed Classification Audit Table

| Registry ID | Edible | Relevant Ingredients / Recipe | MuslimQoL Status | Reason | Evidence / Source | Confidence |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `farmersdelight:apple_cider` | Yes (hand) | minecraft:apple, minecraft:apple, minecraft:sugar | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:apple_pie` | Yes (placed) | 3x #c:crops/wheat, 3x minecraft:apple, 2x minecraft:sugar, farmersdelight:pie_crust; 4x farmersdelight:apple_pie_slice | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:apple_pie_slice` | Yes (hand) | farmersdelight:apple_pie | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:bacon` | Yes (hand) | minecraft:porkchop | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:bacon_and_eggs` | Yes (hand) | #c:foods/cooked_bacon, #c:foods/cooked_bacon, minecraft:bowl, #c:foods/cooked_egg, #c:foods/cooked_egg | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:bacon_sandwich` | Yes (hand) | #c:foods/bread, #c:foods/cooked_bacon, #c:foods/leafy_green, #c:crops/tomato | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:baked_cod_stew` | Yes (hand) | #c:foods/raw_cod, #c:crops/potato, #c:eggs, #c:crops/tomato | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:barbecue_stick` | Yes (hand) | #c:crops/tomato, #c:crops/onion, #c:foods/cooked_meat, minecraft:stick | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:beef_patty` | Yes (hand) | farmersdelight:minced_beef | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:beef_stew` | Yes (hand) | #c:foods/raw_beef, #c:crops/carrot, #c:crops/potato | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:bone_broth` | Yes (hand) | #c:bones, (minecraft:glow_berries | #c:mushrooms | minecraft:hanging_roots | minecraft:glow_lichen) | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:cabbage` | Yes (hand) | farmersdelight:cabbage_crate; farmersdelight:cabbage_leaf, farmersdelight:cabbage_leaf | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cabbage_leaf` | Yes (hand) | farmersdelight:cabbage | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cabbage_rolls` | Yes (hand) | #c:crops/cabbage, (#c:foods/raw_meat | #c:foods/safe_raw_fish | #c:foods/vegetable | #c:mushrooms) | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:cake_slice` | Yes (hand) | minecraft:cake | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:chicken_cuts` | Yes (hand) | minecraft:chicken | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chicken_sandwich` | Yes (hand) | #c:foods/bread, #c:foods/cooked_chicken, #c:foods/leafy_green, #c:crops/carrot | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chicken_soup` | Yes (hand) | #c:foods/raw_chicken, #c:crops/carrot, #c:foods/leafy_green, (#c:foods/vegetable - minecraft:melon_slice) | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chocolate_pie` | Yes (placed) | 3x minecraft:cocoa_beans, 3x #c:drinks/milk, 2x minecraft:sugar, farmersdelight:pie_crust; 4x farmersdelight:chocolate_pie_slice | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:chocolate_pie_slice` | Yes (hand) | farmersdelight:chocolate_pie | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:cod_roll` | Yes (hand) | farmersdelight:cod_slice, farmersdelight:cod_slice, farmersdelight:cooked_rice | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cod_slice` | Yes (hand) | minecraft:cod | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cooked_bacon` | Yes (hand) | farmersdelight:bacon | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:cooked_chicken_cuts` | Yes (hand) | farmersdelight:chicken_cuts; minecraft:cooked_chicken | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:cooked_cod_slice` | Yes (hand) | farmersdelight:cod_slice; minecraft:cooked_cod | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cooked_mutton_chops` | Yes (hand) | farmersdelight:mutton_chops; minecraft:cooked_mutton | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:cooked_rice` | Yes (hand) | #c:crops/rice | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cooked_salmon_slice` | Yes (hand) | farmersdelight:salmon_slice; minecraft:cooked_salmon | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:dog_food` | Yes (hand) | minecraft:rotten_flesh, minecraft:bone_meal, #c:foods/raw_meat, #c:crops/rice | **RESTRICTED** | `carrion` | `data/.../carrion.json` | HIGH |
| `farmersdelight:dumplings` | Yes (hand) | #c:foods/dough, #c:crops/cabbage, #c:crops/onion, (#c:foods/raw_chicken | #c:foods/raw_pork | #c:foods/raw_beef | minecraft:brown_mushroom) | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:egg_sandwich` | Yes (hand) | #c:foods/bread, #c:foods/cooked_egg, #c:foods/cooked_egg | **HALAL** | `audited_permitted_recipe` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fish_stew` | Yes (hand) | #c:foods/safe_raw_fish, farmersdelight:tomato_sauce, #c:crops/onion | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:fried_egg` | Yes (hand) | minecraft:egg | **HALAL** | `audited_permitted_recipe` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fried_rice` | Yes (hand) | #c:crops/rice, #c:eggs, #c:crops/carrot, #c:crops/onion | **HALAL** | `audited_permitted_recipe` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fruit_salad` | Yes (hand) | minecraft:apple, minecraft:melon_slice, minecraft:melon_slice, #c:foods/berry, #c:foods/berry, farmersdelight:pumpkin_slice, minecraft:bowl | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:gleaming_salad` | Yes (hand) | Primary produce or cutting board result | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:gleaming_salad_block` | Yes (placed) | minecraft:glow_berries, minecraft:honey_bottle, minecraft:glow_berries, #c:crops/tomato, minecraft:golden_carrot, #c:crops/beetroot, farmersdelight:cabbage, minecraft:bowl, farmersdelight:cabbage | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:glow_berry_custard` | Yes (placed) | minecraft:glow_berries, #c:drinks/milk, #c:eggs, minecraft:sugar | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:grilled_salmon` | Yes (hand) | #c:foods/cooked_salmon, minecraft:sweet_berries, minecraft:bowl, #c:crops/cabbage, #c:crops/onion | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:ham` | Yes (hand) | Cutting board slice from pork cuts or whole smoked ham | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:hamburger` | Yes (hand) | #c:foods/bread, farmersdelight:beef_patty, #c:foods/leafy_green, #c:crops/tomato, #c:crops/onion | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:honey_cookie` | Yes (hand) | minecraft:honey_bottle, #c:crops/wheat, #c:crops/wheat | **HALAL** | `honey` | `data/.../desserts.json` | HIGH |
| `farmersdelight:honey_glazed_ham` | Yes (hand) | Primary produce or cutting board result | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:honey_glazed_ham_block` | Yes (placed) | minecraft:sweet_berries, minecraft:honey_bottle, minecraft:sweet_berries, minecraft:sweet_berries, farmersdelight:smoked_ham, minecraft:sweet_berries, farmersdelight:cooked_rice, minecraft:bowl, farmersdelight:cooked_rice | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:hot_cocoa` | Yes (hand) | #c:drinks/milk, minecraft:sugar, minecraft:cocoa_beans, minecraft:cocoa_beans | **HALAL** | `audited_permitted_recipe` | `data/.../drinks.json` | HIGH |
| `farmersdelight:kelp_roll` | Yes (hand) | 2x farmersdelight:cooked_rice, #c:foods/vegetable, 3x minecraft:dried_kelp | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:kelp_roll_slice` | Yes (hand) | farmersdelight:kelp_roll | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:melon_juice` | Yes (hand) | minecraft:melon_slice, minecraft:melon_slice, minecraft:sugar, minecraft:melon_slice, minecraft:melon_slice, minecraft:glass_bottle | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:melon_popsicle` | Yes (hand) | 4x minecraft:melon_slice, 2x minecraft:ice, minecraft:stick | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:milk_bottle` | Yes (hand) | minecraft:milk_bucket, minecraft:glass_bottle, minecraft:glass_bottle, minecraft:glass_bottle, minecraft:glass_bottle | **HALAL** | `audited_permitted_recipe` | `data/.../drinks.json` | HIGH |
| `farmersdelight:minced_beef` | Yes (hand) | minecraft:beef | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:mixed_salad` | Yes (hand) | #c:foods/leafy_green, #c:crops/tomato, #c:crops/beetroot, minecraft:bowl | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:mushroom_rice` | Yes (hand) | minecraft:brown_mushroom, minecraft:red_mushroom, #c:crops/rice, (minecraft:carrot | minecraft:potato) | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:mutton_chops` | Yes (hand) | minecraft:mutton | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:mutton_wrap` | Yes (hand) | #c:foods/bread, #c:foods/cooked_mutton, #c:foods/leafy_green, #c:crops/onion | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:nether_salad` | Yes (hand) | minecraft:crimson_fungus, minecraft:warped_fungus, minecraft:bowl | **DOUBTFUL** | `toxic_plant` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:noodle_soup` | Yes (hand) | #c:foods/pasta, #c:eggs, minecraft:dried_kelp, #c:foods/raw_pork | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:onion` | Yes (hand) | farmersdelight:wild_onions; farmersdelight:onion_crate | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:onion_soup` | Yes (hand) | #c:crops/onion, #c:crops/onion, #c:foods/bread, #c:drinks/milk | **HALAL** | `audited_permitted_recipe` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:pasta_with_meatballs` | Yes (hand) | farmersdelight:minced_beef, #c:foods/pasta, farmersdelight:tomato_sauce | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:pasta_with_mutton_chop` | Yes (hand) | #c:foods/raw_mutton, #c:foods/pasta, farmersdelight:tomato_sauce | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:pie_crust` | Yes (placed) | 3x #c:crops/wheat, #c:drinks/milk | **HALAL** | `audited_permitted_recipe` | `data/.../plants.json` | HIGH |
| `farmersdelight:pumpkin_pie_slice` | Yes (hand) | minecraft:pumpkin_pie | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:pumpkin_slice` | Yes (hand) | minecraft:pumpkin; No direct ingredients | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:pumpkin_soup` | Yes (hand) | farmersdelight:pumpkin_slice, #c:foods/leafy_green, #c:foods/raw_pork, #c:drinks/milk | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:ratatouille` | Yes (hand) | #c:crops/tomato, #c:crops/onion, #c:crops/beetroot, (#c:foods/vegetable - minecraft:melon_slice) | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:raw_pasta` | Yes (hand) | #c:foods/dough | **HALAL** | `audited_permitted_recipe` | `data/.../plants.json` | HIGH |
| `farmersdelight:rice_roll_medley_block` | Yes (placed) | farmersdelight:kelp_roll_slice, farmersdelight:kelp_roll_slice, farmersdelight:kelp_roll_slice, farmersdelight:salmon_roll, farmersdelight:salmon_roll, farmersdelight:salmon_roll, farmersdelight:cod_roll, minecraft:bowl, farmersdelight:cod_roll | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:roast_chicken` | Yes (hand) | Primary produce or cutting board result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:roast_chicken_block` | Yes (placed) | #c:crops/onion, #c:eggs, #c:foods/bread, #c:crops/carrot, minecraft:cooked_chicken, minecraft:baked_potato, #c:crops/carrot, minecraft:bowl, minecraft:baked_potato | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:roasted_mutton_chops` | Yes (hand) | farmersdelight:cooked_mutton_chops, #c:crops/beetroot, minecraft:bowl, farmersdelight:cooked_rice, #c:crops/tomato | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:salmon_roll` | Yes (hand) | farmersdelight:salmon_slice, farmersdelight:salmon_slice, farmersdelight:cooked_rice | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:salmon_slice` | Yes (hand) | minecraft:salmon | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:shepherds_pie` | Yes (placed) | Primary produce or cutting board result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:shepherds_pie_block` | Yes (placed) | minecraft:baked_potato, #c:drinks/milk, minecraft:baked_potato, #c:foods/cooked_mutton, #c:foods/cooked_mutton, #c:foods/cooked_mutton, #c:crops/onion, minecraft:bowl, #c:crops/onion | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:smoked_ham` | Yes (hand) | farmersdelight:ham | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:squid_ink_pasta` | Yes (hand) | #c:foods/safe_raw_fish, #c:foods/pasta, #c:crops/tomato, minecraft:ink_sac | **UNKNOWN** | `seafood_policy_unspecified` | `data/.../seafood_unknown.json` | HIGH |
| `farmersdelight:steak_and_potatoes` | Yes (hand) | minecraft:baked_potato, minecraft:cooked_beef, minecraft:bowl, #c:crops/onion, farmersdelight:cooked_rice | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:stuffed_potato` | Yes (hand) | minecraft:baked_potato, #c:foods/cooked_beef, #c:drinks/milk | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:stuffed_pumpkin` | Yes (hand) | Primary produce or cutting board result | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:stuffed_pumpkin_block` | Yes (placed) | #c:crops/rice, #c:crops/onion, minecraft:brown_mushroom, #c:crops/potato, #c:foods/berry, (#c:foods/vegetable - minecraft:melon_slice) | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:sweet_berry_cheesecake` | Yes (placed) | 6x minecraft:sweet_berries, 2x #c:drinks/milk, farmersdelight:pie_crust; 4x farmersdelight:sweet_berry_cheesecake_slice | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:sweet_berry_cheesecake_slice` | Yes (hand) | farmersdelight:sweet_berry_cheesecake | **HALAL** | `audited_permitted_recipe` | `data/.../desserts.json` | HIGH |
| `farmersdelight:sweet_berry_cookie` | Yes (hand) | minecraft:sweet_berries, #c:crops/wheat, #c:crops/wheat | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:tomato` | Yes (hand) | farmersdelight:wild_tomatoes; farmersdelight:tomato_crate | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:tomato_sauce` | Yes (hand) | #c:crops/tomato, #c:crops/tomato | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:vegetable_noodles` | Yes (hand) | #c:crops/carrot, #c:mushrooms, #c:foods/pasta, #c:foods/leafy_green, (#c:foods/vegetable - minecraft:melon_slice) | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:vegetable_soup` | Yes (hand) | #c:crops/carrot, #c:crops/potato, #c:crops/beetroot, #c:foods/leafy_green | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:wheat_dough` | Yes (hand) | #c:crops/wheat, #c:crops/wheat, #c:crops/wheat, #c:eggs | **HALAL** | `audited_permitted_recipe` | `data/.../plants.json` | HIGH |

---

## Ingredient Traversal & Recipe Analysis Details

### 1. NeoForge Compound Ingredients (`neoforge:compound`)
Farmer's Delight utilizes compound ingredients for dishes that accept alternative fillings:
* **`farmersdelight:dumplings`**: Recipe contains filling alternatives: `#c:foods/raw_chicken`, `#c:foods/raw_pork`, `#c:foods/raw_beef`, and `minecraft:brown_mushroom`. Because the recipe permits pork or ordinary livestock meat interchangeably, the resulting output ItemStack has no provenance tag and is conservatively classified as **`DOUBTFUL`** (`uncertain_ingredients`).
* **`farmersdelight:cabbage_rolls`**: Filling alternatives include `#c:foods/raw_meat`, `#c:foods/safe_raw_fish`, `#c:foods/vegetable`, and `#c:mushrooms`. Classified as **`DOUBTFUL`** (`uncertain_ingredients`).
* **`farmersdelight:bone_broth`**: Soup base combines `#c:bones` with alternatives (`minecraft:glow_berries`, `#c:mushrooms`, `minecraft:hanging_roots`, `minecraft:glow_lichen`). Classified as **`DOUBTFUL`** (`uncertain_ingredients`).

### 2. NeoForge Difference Ingredients (`neoforge:difference`)
Dishes like **`farmersdelight:ratatouille`**, **`farmersdelight:vegetable_noodles`**, **`farmersdelight:chicken_soup`**, and **`farmersdelight:stuffed_pumpkin_block`** use difference ingredients such as `(#c:foods/vegetable - minecraft:melon_slice)`. The audit tool recursively traverses both `base` and `subtracted` sub-ingredients.

### 3. Explicit Pork Recipes
* **`farmersdelight:pumpkin_soup`** and **`farmersdelight:noodle_soup`** explicitly mandate `#c:foods/raw_pork`. Classified as **`RESTRICTED`** (`swine`).
* Pork-derived items (**`bacon`**, **`cooked_bacon`**, **`bacon_and_eggs`**, **`bacon_sandwich`**, **`ham`**, **`smoked_ham`**, **`honey_glazed_ham`**, **`honey_glazed_ham_block`**) are classified as **`RESTRICTED`** (`swine`).

### 4. Non-Fish Seafood Policy
* **`farmersdelight:squid_ink_pasta`** combines `#c:foods/safe_raw_fish` with `minecraft:ink_sac`. Because squid is a cephalopod and MuslimQoL does not currently implement a jurisprudence-specific non-fish seafood ruling, it is conservatively classified as **`UNKNOWN`** (`seafood_policy_unspecified`) rather than assuming universal permissibility.

### 5. Dairy and Egg Recipe Audits
Items containing eggs or dairy (e.g., **`fried_egg`**, **`egg_sandwich`**, **`fried_rice`**, **`onion_soup`**, **`milk_bottle`**, **`hot_cocoa`**, **`sweet_berry_cheesecake`**, **`chocolate_pie`**, **`cake_slice`**, **`pumpkin_pie_slice`**) are classified as **`HALAL`** with reason **`audited_permitted_recipe`** rather than mislabeling them as `plant_based`. Pure crops and vegetable preparations retain **`plant_based`**.

---

## Non-Food Items Audited (Excluded from Food Classification)

The following 96 Farmer's Delight items were audited and confirmed as **non-food** (tools, blocks, storage crates, cabinets, seeds, utility items, or animal feed not consumed by players):

* **Cooking & Storage Blocks**: `stove`, `cooking_pot`, `skillet`, `cutting_board`, `wooden_basket`, `bamboo_basket`, `carrot_crate`, `potato_crate`, `beetroot_crate`, `cabbage_crate`, `tomato_crate`, `onion_crate`, `rice_bale`, `rice_bag`, `straw_bale`, `safety_net`
* **Cabinets (12 wood variants)**: `oak_cabinet`, `spruce_cabinet`, `birch_cabinet`, `jungle_cabinet`, `acacia_cabinet`, `dark_oak_cabinet`, `mangrove_cabinet`, `cherry_cabinet`, `bamboo_cabinet`, `crimson_cabinet`, `warped_cabinet`
* **Tatami & Rugs**: `tatami`, `full_tatami_mat`, `half_tatami_mat`, `canvas_rug`
* **Fences & Nets**: `rope_fence`, `rope_fence_gate`, `rope`, `safety_net`
* **Soil & Compost**: `organic_compost`, `rich_soil`, `rich_soil_farmland`
* **Canvas Signs (16 dyes + plain & hanging)**: 34 canvas sign items
* **Tools**: `flint_knife`, `iron_knife`, `diamond_knife`, `netherite_knife`, `golden_knife`
* **Crafting Materials**: `straw`, `canvas`, `tree_bark`, `rice_panicle`
* **Crops / Seeds / Colonies**: `cabbage_seeds`, `tomato_seeds`, `rice`, `wild_cabbages`, `wild_onions`, `wild_tomatoes`, `wild_carrots`, `wild_potatoes`, `wild_beetroots`, `wild_rice`, `brown_mushroom_colony`, `red_mushroom_colony`, `sandy_shrub`
* **Utility / Projectiles / Animal Feed**:
  * `rotten_tomato`: Ranged projectile weapon (`ProjectileItem`), not consumable.
  * `horse_feed`: Animal feed applied to horses via entity interaction, not consumable by player.
  * `debug_pumpkin_pie`: Hidden internal block-item.
