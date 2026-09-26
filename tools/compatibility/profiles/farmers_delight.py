"""
Farmer's Delight Compatibility Profile.

Supplies mod-specific contextual notes and metadata for Farmer's Delight 1.3.4
without polluting the generic audit engine.
"""

from typing import Dict, List, Optional, Set


MOD_ID = "farmersdelight"
TARGET_VERSION = "1.21.1-1.3.4"
REFERENCE_JAR_SHA256 = "139ad7696462c89c03eea463f805abffa552526c5dadaadae221dd9624cb197c"
EXPECTED_TOTAL_ITEMS = 185
EXPECTED_EDIBLE_ITEMS = 89

SPECIAL_NOTES: Dict[str, str] = {
    "farmersdelight:cabbage": "Harvested crop; also obtainable from wild cabbages",
    "farmersdelight:tomato": "Harvested crop; also obtainable from wild tomatoes",
    "farmersdelight:onion": "Harvested crop; also obtainable from wild onions",
    "farmersdelight:rice": "Harvested crop; milling yields rice and straw",
    "farmersdelight:bacon": "Cutting board slice from #c:foods/raw_pork (swine)",
    "farmersdelight:cooked_bacon": "Smoked/cooked bacon or cutting board slice",
    "farmersdelight:cod_slice": "Cutting board slice from #c:foods/raw_cod (raw fish)",
    "farmersdelight:cooked_cod_slice": "Smoked/cooked cod slice or cutting board slice",
    "farmersdelight:salmon_slice": "Cutting board slice from #c:foods/raw_salmon (raw fish)",
    "farmersdelight:cooked_salmon_slice": "Smoked/cooked salmon slice or cutting board slice",
    "farmersdelight:chicken_cuts": "Cutting board slice from #c:foods/raw_chicken (livestock poultry)",
    "farmersdelight:cooked_chicken_cuts": "Smoked/cooked chicken cuts or cutting board slice",
    "farmersdelight:mutton_chops": "Cutting board slice from #c:foods/raw_mutton (livestock meat)",
    "farmersdelight:cooked_mutton_chops": "Smoked/cooked mutton chops or cutting board slice",
    "farmersdelight:minced_beef": "Cutting board mincing from #c:foods/raw_beef (livestock meat)",
    "farmersdelight:beef_patty": "Furnace / campfire cooking of farmersdelight:minced_beef",
    "farmersdelight:ham": "Cutting board slice from pork cuts or whole smoked ham",
    "farmersdelight:smoked_ham": "Smoked pork leg on campfire/stove",
    "farmersdelight:cabbage_leaf": "Cutting board strip from farmersdelight:cabbage",
    "farmersdelight:pumpkin_slice": "Cutting board slice from minecraft:pumpkin",
    "farmersdelight:brown_mushroom_colony": "Harvestable mushroom colony block-item",
    "farmersdelight:red_mushroom_colony": "Harvestable mushroom colony block-item",
}

PLACEABLE_FEASTS: Set[str] = {
    "farmersdelight:roast_chicken_block",
    "farmersdelight:stuffed_pumpkin_block",
    "farmersdelight:honey_glazed_ham_block",
    "farmersdelight:shepherds_pie_block",
    "farmersdelight:rice_roll_medley_block",
    "farmersdelight:apple_pie",
    "farmersdelight:sweet_berry_cheesecake",
    "farmersdelight:chocolate_pie",
    "farmersdelight:glow_berry_custard",
}

FOOD_TAG_ROOTS: List[str] = [
    "c:foods",
    "c:drinks",
    "farmersdelight:meals",
    "farmersdelight:snacks",
    "farmersdelight:sweets",
    "farmersdelight:feasts",
    "farmersdelight:drinks",
]
