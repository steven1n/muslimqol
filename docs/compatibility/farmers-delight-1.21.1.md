# Farmer's Delight 1.21.1 Food Classification Audit

> [!NOTE]
> **Disclaimer**: These are conservative gameplay classifications based on in-game recipe composition and are not a substitute for religious guidance or real-world halal certification.

This document details the food classification audit for **Farmer's Delight 1.3.4** on **Minecraft 1.21.1** (NeoForge).

## Audit Scope & Summary

* **Target Mod**: Farmer's Delight (`farmersdelight`)
* **Tested Version**: `1.21.1-1.3.4`
* **Platform**: NeoForge 21.1.x / Java 21
* **Compatibility Namespace**: `muslimqol_farmersdelight`
* **Total Audited Edible Items**: 89
  * Hand-edible items: 80
  * Placeable feasts & whole pies: 9
* **Classification Breakdown**:
  * **HALAL**: 53 (11 seafood/fish + 42 crops, grains, dairy, sweets, and prepared vegetarian meals)
  * **RESTRICTED**: 11 (swine-derived cuts, bacon meals, pork ramen, pumpkin soup with pork, dog food with carrion)
  * **DOUBTFUL**: 5 (uncertain mixtures like dumplings, cabbage rolls, barbecue sticks, bone broth, and toxic nether salad)
  * **UNKNOWN**: 20 (ordinary uncertified livestock meats: beef, chicken, and mutton dishes)
  * **Unclassified**: 0

---

## Detailed Classification Audit Table

| Registry ID | Edible | Relevant Ingredients / Recipe | MuslimQoL Status | Reason | Evidence / Source | Confidence |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `farmersdelight:apple_cider` | Yes (hand) | cooking: ['minecraft:apple', 'minecraft:apple', 'minecraft:sugar'] | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:apple_pie` | Yes (placed) | crafting_shaped: ['#c:crops/wheat', 'farmersdelight:pie_crust', 'minecraft:apple', 'minecraft:sugar']; crafting_shaped: ['farmersdelight:apple_pie_slice'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:apple_pie_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:bacon` | Yes (hand) | Raw / knife cutting result | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:bacon_and_eggs` | Yes (hand) | crafting_shapeless: ['#c:foods/cooked_bacon', '#c:foods/cooked_bacon', 'minecraft:bowl', '#c:foods/cooked_egg', '#c:foods/cooked_egg'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:bacon_sandwich` | Yes (hand) | crafting_shapeless: ['#c:foods/bread', '#c:foods/cooked_bacon', '#c:foods/leafy_green', '#c:crops/tomato'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:baked_cod_stew` | Yes (hand) | cooking: ['#c:foods/raw_cod', '#c:crops/potato', '#c:eggs', '#c:crops/tomato'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:barbecue_stick` | Yes (hand) | crafting_shapeless: ['#c:crops/tomato', '#c:crops/onion', '#c:foods/cooked_meat', 'minecraft:stick'] | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:beef_patty` | Yes (hand) | smelting: ['farmersdelight:minced_beef']; campfire_cooking: ['farmersdelight:minced_beef']; smoking: ['farmersdelight:minced_beef'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:beef_stew` | Yes (hand) | cooking: ['#c:foods/raw_beef', '#c:crops/carrot', '#c:crops/potato'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:bone_broth` | Yes (hand) | cooking: ['#c:bones'] | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:cabbage` | Yes (hand) | crafting_shapeless: ['farmersdelight:cabbage_crate']; crafting_shapeless: ['farmersdelight:cabbage_leaf', 'farmersdelight:cabbage_leaf'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cabbage_leaf` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cabbage_rolls` | Yes (hand) | cooking: ['#c:crops/cabbage'] | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:cake_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:chicken_cuts` | Yes (hand) | Raw / knife cutting result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chicken_sandwich` | Yes (hand) | crafting_shapeless: ['#c:foods/bread', '#c:foods/cooked_chicken', '#c:foods/leafy_green', '#c:crops/carrot'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chicken_soup` | Yes (hand) | cooking: ['#c:foods/raw_chicken', '#c:crops/carrot', '#c:foods/leafy_green'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:chocolate_pie` | Yes (placed) | crafting_shaped: ['farmersdelight:pie_crust', 'minecraft:cocoa_beans', '#c:drinks/milk', 'minecraft:sugar']; crafting_shaped: ['farmersdelight:chocolate_pie_slice'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:chocolate_pie_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:cod_roll` | Yes (hand) | crafting_shapeless: ['farmersdelight:cod_slice', 'farmersdelight:cod_slice', 'farmersdelight:cooked_rice'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cod_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cooked_bacon` | Yes (hand) | smelting: ['farmersdelight:bacon']; campfire_cooking: ['farmersdelight:bacon']; smoking: ['farmersdelight:bacon'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:cooked_chicken_cuts` | Yes (hand) | smelting: ['farmersdelight:chicken_cuts']; campfire_cooking: ['farmersdelight:chicken_cuts']; smoking: ['farmersdelight:chicken_cuts'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:cooked_cod_slice` | Yes (hand) | smelting: ['farmersdelight:cod_slice']; campfire_cooking: ['farmersdelight:cod_slice']; smoking: ['farmersdelight:cod_slice'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:cooked_mutton_chops` | Yes (hand) | smelting: ['farmersdelight:mutton_chops']; campfire_cooking: ['farmersdelight:mutton_chops']; smoking: ['farmersdelight:mutton_chops'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:cooked_rice` | Yes (hand) | cooking: ['#c:crops/rice'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:cooked_salmon_slice` | Yes (hand) | smelting: ['farmersdelight:salmon_slice']; campfire_cooking: ['farmersdelight:salmon_slice']; smoking: ['farmersdelight:salmon_slice'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:dog_food` | Yes (hand) | cooking: ['minecraft:rotten_flesh', 'minecraft:bone_meal', '#c:foods/raw_meat', '#c:crops/rice'] | **RESTRICTED** | `carrion` | `data/.../carrion.json` | HIGH |
| `farmersdelight:dumplings` | Yes (hand) | cooking: ['#c:foods/dough', '#c:crops/cabbage', '#c:crops/onion'] | **DOUBTFUL** | `unknown_ingredients` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:egg_sandwich` | Yes (hand) | crafting_shapeless: ['#c:foods/bread', '#c:foods/cooked_egg', '#c:foods/cooked_egg'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fish_stew` | Yes (hand) | cooking: ['#c:foods/safe_raw_fish', 'farmersdelight:tomato_sauce', '#c:crops/onion'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:fried_egg` | Yes (hand) | smelting: ['minecraft:egg']; campfire_cooking: ['minecraft:egg']; smoking: ['minecraft:egg'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fried_rice` | Yes (hand) | cooking: ['#c:crops/rice', '#c:eggs', '#c:crops/carrot', '#c:crops/onion'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:fruit_salad` | Yes (hand) | crafting_shapeless: ['minecraft:apple', 'minecraft:melon_slice', 'minecraft:melon_slice', '#c:foods/berry', '#c:foods/berry', 'farmersdelight:pumpkin_slice', 'minecraft:bowl'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:gleaming_salad` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:gleaming_salad_block` | Yes (placed) | crafting_shapeless: ['minecraft:glow_berries', 'minecraft:honey_bottle', 'minecraft:glow_berries', '#c:crops/tomato', 'minecraft:golden_carrot', '#c:crops/beetroot', 'farmersdelight:cabbage', 'minecraft:bowl', 'farmersdelight:cabbage'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:glow_berry_custard` | Yes (hand) | cooking: ['minecraft:glow_berries', '#c:drinks/milk', '#c:eggs', 'minecraft:sugar'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:grilled_salmon` | Yes (hand) | crafting_shapeless: ['#c:foods/cooked_salmon', 'minecraft:sweet_berries', 'minecraft:bowl', '#c:crops/cabbage', '#c:crops/onion'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:ham` | Yes (hand) | Raw / knife cutting result | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:hamburger` | Yes (hand) | crafting_shapeless: ['#c:foods/bread', 'farmersdelight:beef_patty', '#c:foods/leafy_green', '#c:crops/tomato', '#c:crops/onion'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:honey_cookie` | Yes (hand) | crafting_shapeless: ['minecraft:honey_bottle', '#c:crops/wheat', '#c:crops/wheat'] | **HALAL** | `honey` | `data/.../desserts.json` | HIGH |
| `farmersdelight:honey_glazed_ham` | Yes (hand) | Raw / knife cutting result | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:honey_glazed_ham_block` | Yes (placed) | crafting_shapeless: ['minecraft:sweet_berries', 'minecraft:honey_bottle', 'minecraft:sweet_berries', 'minecraft:sweet_berries', 'farmersdelight:smoked_ham', 'minecraft:sweet_berries', 'farmersdelight:cooked_rice', 'minecraft:bowl', 'farmersdelight:cooked_rice'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:hot_cocoa` | Yes (hand) | cooking: ['#c:drinks/milk', 'minecraft:sugar', 'minecraft:cocoa_beans', 'minecraft:cocoa_beans'] | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:kelp_roll` | Yes (hand) | crafting_shaped: ['minecraft:dried_kelp', 'farmersdelight:cooked_rice', '#c:foods/vegetable'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:kelp_roll_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:melon_juice` | Yes (hand) | crafting_shapeless: ['minecraft:melon_slice', 'minecraft:melon_slice', 'minecraft:sugar', 'minecraft:melon_slice', 'minecraft:melon_slice', 'minecraft:glass_bottle'] | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:melon_popsicle` | Yes (hand) | crafting_shaped: ['minecraft:stick', 'minecraft:ice', 'minecraft:melon_slice'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:milk_bottle` | Yes (hand) | crafting_shapeless: ['minecraft:milk_bucket', 'minecraft:glass_bottle', 'minecraft:glass_bottle', 'minecraft:glass_bottle', 'minecraft:glass_bottle'] | **HALAL** | `plant_based` | `data/.../drinks.json` | HIGH |
| `farmersdelight:minced_beef` | Yes (hand) | Raw / knife cutting result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:mixed_salad` | Yes (hand) | crafting_shapeless: ['#c:foods/leafy_green', '#c:crops/tomato', '#c:crops/beetroot', 'minecraft:bowl'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:mushroom_rice` | Yes (hand) | cooking: ['minecraft:brown_mushroom', 'minecraft:red_mushroom', '#c:crops/rice'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:mutton_chops` | Yes (hand) | Raw / knife cutting result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:mutton_wrap` | Yes (hand) | crafting_shapeless: ['#c:foods/bread', '#c:foods/cooked_mutton', '#c:foods/leafy_green', '#c:crops/onion'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:nether_salad` | Yes (hand) | crafting_shapeless: ['minecraft:crimson_fungus', 'minecraft:warped_fungus', 'minecraft:bowl'] | **DOUBTFUL** | `toxic_plant` | `data/.../doubtful.json` | HIGH |
| `farmersdelight:noodle_soup` | Yes (hand) | cooking: ['#c:foods/pasta', '#c:eggs', 'minecraft:dried_kelp', '#c:foods/raw_pork'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:onion` | Yes (hand) | crafting_shapeless: ['farmersdelight:onion_crate'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:onion_soup` | Yes (hand) | cooking: ['#c:crops/onion', '#c:crops/onion', '#c:foods/bread', '#c:drinks/milk'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:pasta_with_meatballs` | Yes (hand) | cooking: ['farmersdelight:minced_beef', '#c:foods/pasta', 'farmersdelight:tomato_sauce'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:pasta_with_mutton_chop` | Yes (hand) | cooking: ['#c:foods/raw_mutton', '#c:foods/pasta', 'farmersdelight:tomato_sauce'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:pie_crust` | Yes (hand) | crafting_shaped: ['#c:drinks/milk', '#c:crops/wheat'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:pumpkin_pie_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:pumpkin_slice` | Yes (hand) | metal_press: [] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:pumpkin_soup` | Yes (hand) | cooking: ['farmersdelight:pumpkin_slice', '#c:foods/leafy_green', '#c:foods/raw_pork', '#c:drinks/milk'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:ratatouille` | Yes (hand) | cooking: ['#c:crops/tomato', '#c:crops/onion', '#c:crops/beetroot'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:raw_pasta` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:rice_roll_medley_block` | Yes (placed) | crafting_shapeless: ['farmersdelight:kelp_roll_slice', 'farmersdelight:kelp_roll_slice', 'farmersdelight:kelp_roll_slice', 'farmersdelight:salmon_roll', 'farmersdelight:salmon_roll', 'farmersdelight:salmon_roll', 'farmersdelight:cod_roll', 'minecraft:bowl', 'farmersdelight:cod_roll'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:roast_chicken` | Yes (hand) | Raw / knife cutting result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:roast_chicken_block` | Yes (placed) | crafting_shapeless: ['#c:crops/onion', '#c:eggs', '#c:foods/bread', '#c:crops/carrot', 'minecraft:cooked_chicken', 'minecraft:baked_potato', '#c:crops/carrot', 'minecraft:bowl', 'minecraft:baked_potato'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:roasted_mutton_chops` | Yes (hand) | crafting_shapeless: ['farmersdelight:cooked_mutton_chops', '#c:crops/beetroot', 'minecraft:bowl', 'farmersdelight:cooked_rice', '#c:crops/tomato'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:salmon_roll` | Yes (hand) | crafting_shapeless: ['farmersdelight:salmon_slice', 'farmersdelight:salmon_slice', 'farmersdelight:cooked_rice'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:salmon_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:shepherds_pie` | Yes (hand) | Raw / knife cutting result | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:shepherds_pie_block` | Yes (placed) | crafting_shapeless: ['minecraft:baked_potato', '#c:drinks/milk', 'minecraft:baked_potato', '#c:foods/cooked_mutton', '#c:foods/cooked_mutton', '#c:foods/cooked_mutton', '#c:crops/onion', 'minecraft:bowl', '#c:crops/onion'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:smoked_ham` | Yes (hand) | smoking: ['farmersdelight:ham'] | **RESTRICTED** | `swine` | `data/.../pork.json` | HIGH |
| `farmersdelight:squid_ink_pasta` | Yes (hand) | cooking: ['#c:foods/safe_raw_fish', '#c:foods/pasta', '#c:crops/tomato', 'minecraft:ink_sac'] | **HALAL** | `fish` | `data/.../fish.json` | HIGH |
| `farmersdelight:steak_and_potatoes` | Yes (hand) | crafting_shapeless: ['minecraft:baked_potato', 'minecraft:cooked_beef', 'minecraft:bowl', '#c:crops/onion', 'farmersdelight:cooked_rice'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:stuffed_potato` | Yes (hand) | crafting_shapeless: ['minecraft:baked_potato', '#c:foods/cooked_beef', '#c:drinks/milk'] | **UNKNOWN** | `unspecified_meat` | `data/.../meat_unknown.json` | HIGH |
| `farmersdelight:stuffed_pumpkin` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:stuffed_pumpkin_block` | Yes (placed) | cooking: ['#c:crops/rice', '#c:crops/onion', 'minecraft:brown_mushroom', '#c:crops/potato', '#c:foods/berry'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:sweet_berry_cheesecake` | Yes (placed) | crafting_shaped: ['farmersdelight:pie_crust', '#c:drinks/milk', 'minecraft:sweet_berries']; crafting_shaped: ['farmersdelight:sweet_berry_cheesecake_slice'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:sweet_berry_cheesecake_slice` | Yes (hand) | Raw / knife cutting result | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:sweet_berry_cookie` | Yes (hand) | crafting_shapeless: ['minecraft:sweet_berries', '#c:crops/wheat', '#c:crops/wheat'] | **HALAL** | `plant_based` | `data/.../desserts.json` | HIGH |
| `farmersdelight:tomato` | Yes (hand) | crafting_shapeless: ['farmersdelight:tomato_crate'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:tomato_sauce` | Yes (hand) | cooking: ['#c:crops/tomato', '#c:crops/tomato'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |
| `farmersdelight:vegetable_noodles` | Yes (hand) | cooking: ['#c:crops/carrot', '#c:mushrooms', '#c:foods/pasta', '#c:foods/leafy_green'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:vegetable_soup` | Yes (hand) | cooking: ['#c:crops/carrot', '#c:crops/potato', '#c:crops/beetroot', '#c:foods/leafy_green'] | **HALAL** | `plant_based` | `data/.../prepared_meals.json` | HIGH |
| `farmersdelight:wheat_dough` | Yes (hand) | crafting_shapeless: ['#c:crops/wheat', '#c:crops/wheat', '#c:crops/wheat', '#c:eggs'] | **HALAL** | `plant_based` | `data/.../plants.json` | HIGH |

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
