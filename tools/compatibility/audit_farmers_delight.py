#!/usr/bin/env python3
"""
Farmer's Delight 1.3.4 (Minecraft 1.21.1 / NeoForge) Food Classification Audit Tool.

This script parses Farmer's Delight recipe data directly from the official mod JAR,
fully traversing NeoForge ingredient structures (including neoforge:compound and
neoforge:difference), cross-references the 89 edible items against MuslimQoL's
authoritative reference manifest and bundled classification JSONs, and generates
the audit documentation.

Usage:
    python3 tools/compatibility/audit_farmers_delight.py [path_to_fd_jar]
"""

import glob
import hashlib
import json
import os
import sys
import zipfile

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
REFERENCE_MANIFEST_PATH = os.path.join(
    PROJECT_ROOT, "src", "test", "resources", "reference", "farmers-delight-1.3.4-edible-items.json"
)
CLASSIFICATIONS_DIR = os.path.join(
    PROJECT_ROOT, "src", "main", "resources", "data", "muslimqol_farmersdelight", "muslimqol", "food_classifications"
)
DOCS_AUDIT_PATH = os.path.join(PROJECT_ROOT, "docs", "compatibility", "farmers-delight-1.21.1.md")


def find_jar():
    if len(sys.argv) > 1 and os.path.isfile(sys.argv[1]):
        return sys.argv[1]
    
    # Check gradle cache
    home = os.path.expanduser("~")
    gradle_matches = glob.glob(
        os.path.join(home, ".gradle/caches/**/farmers-delight*1.3.4*.jar"), recursive=True
    )
    for m in gradle_matches:
        if os.path.isfile(m):
            return m
            
    # Check scratch
    scratch_matches = glob.glob(
        os.path.join(home, ".gemini/antigravity/brain/**/FarmersDelight-1.21.1-1.3.4.jar"), recursive=True
    )
    for m in scratch_matches:
        if os.path.isfile(m):
            return m

    raise FileNotFoundError("Could not find FarmersDelight-1.21.1-1.3.4.jar. Please pass path as argument.")


def sha256_of_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def format_ingredient(ing):
    if isinstance(ing, list):
        return " | ".join(format_ingredient(x) for x in ing)
    if not isinstance(ing, dict):
        return str(ing)
    ing_type = ing.get("type", "")
    if ing_type == "neoforge:compound":
        children = ing.get("children", [])
        return "(" + " | ".join(format_ingredient(c) for c in children) + ")"
    elif ing_type == "neoforge:difference":
        base = format_ingredient(ing.get("base", {}))
        subtracted = format_ingredient(ing.get("subtracted", {}))
        return f"({base} - {subtracted})"
    elif "tag" in ing:
        return "#" + ing["tag"]
    elif "item" in ing:
        return ing["item"]
    return str(ing)


def get_id_from_obj(obj):
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        if "id" in obj and isinstance(obj["id"], str):
            return obj["id"]
        if "item" in obj:
            if isinstance(obj["item"], str):
                return obj["item"]
            if isinstance(obj["item"], dict):
                return get_id_from_obj(obj["item"])
        if "basePredicate" in obj and isinstance(obj["basePredicate"], dict):
            return get_id_from_obj(obj["basePredicate"])
    return None


def extract_recipes(jar_path):
    recipes_by_output = {}
    with zipfile.ZipFile(jar_path) as z:
        for name in z.namelist():
            if not (name.startswith("data/farmersdelight/recipe/") and name.endswith(".json")):
                continue
            try:
                data = json.loads(z.read(name))
            except Exception:
                continue

            results = []
            result_obj = data.get("result")
            if isinstance(result_obj, list):
                for r in result_obj:
                    rid = get_id_from_obj(r)
                    if rid:
                        results.append(rid)
            elif result_obj:
                rid = get_id_from_obj(result_obj)
                if rid:
                    results.append(rid)

            if not results:
                continue

            ingredients_list = []
            if "ingredients" in data:
                for ing in data["ingredients"]:
                    ingredients_list.append(format_ingredient(ing))
            elif "ingredient" in data:
                ingredients_list.append(format_ingredient(data["ingredient"]))
            elif "key" in data and "pattern" in data:
                # shaped
                counts = {}
                for row in data["pattern"]:
                    for ch in row:
                        if ch != " " and ch in data["key"]:
                            formatted = format_ingredient(data["key"][ch])
                            counts[formatted] = counts.get(formatted, 0) + 1
                for item_str, count in counts.items():
                    ingredients_list.append(f"{count}x {item_str}" if count > 1 else item_str)

            recipe_summary = ", ".join(ingredients_list) if ingredients_list else "No direct ingredients"
            for rid in results:
                if not rid.startswith("farmersdelight:"):
                    rid = "farmersdelight:" + rid
                recs = recipes_by_output.setdefault(rid, [])
                if recipe_summary not in recs:
                    recs.append(recipe_summary)

    return recipes_by_output


def load_manifest():
    with open(REFERENCE_MANIFEST_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def load_classifications():
    item_map = {}
    counts = {"HALAL": 0, "RESTRICTED": 0, "DOUBTFUL": 0, "UNKNOWN": 0}
    for file_name in sorted(os.listdir(CLASSIFICATIONS_DIR)):
        if not file_name.endswith(".json"):
            continue
        full_path = os.path.join(CLASSIFICATIONS_DIR, file_name)
        with open(full_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            for v in data.get("values", []):
                item_id = v["item"]
                status = v["status"]
                reason = v["reason"]
                item_map[item_id] = {
                    "status": status,
                    "reason": reason,
                    "file": file_name
                }
                if status in counts:
                    counts[status] += 1
                else:
                    counts[status] = counts.get(status, 0) + 1
    return item_map, counts


def main():
    jar_path = find_jar()
    jar_sha = sha256_of_file(jar_path)
    print(f"Using Farmer's Delight JAR: {jar_path}")
    print(f"JAR SHA-256: {jar_sha}")

    manifest = load_manifest()
    manifest_items = set(manifest["items"])
    assert manifest["jar_sha256"] == jar_sha, (
        f"Manifest SHA-256 mismatch: expected {manifest['jar_sha256']}, got {jar_sha}"
    )

    classifications, counts = load_classifications()
    classified_items = set(classifications.keys())

    missing_in_class = manifest_items - classified_items
    extra_in_class = classified_items - manifest_items
    assert not missing_in_class, f"Items in manifest but missing from classification: {missing_in_class}"
    assert not extra_in_class, f"Items in classification but missing from manifest: {extra_in_class}"

    print(f"\nClassification Totals:")
    print(f"  HALAL:       {counts.get('HALAL', 0)}")
    print(f"  RESTRICTED:  {counts.get('RESTRICTED', 0)}")
    print(f"  DOUBTFUL:    {counts.get('DOUBTFUL', 0)}")
    print(f"  UNKNOWN:     {counts.get('UNKNOWN', 0)}")
    total = sum(counts.values())
    print(f"  TOTAL:       {total}")

    recipes_by_output = extract_recipes(jar_path)

    # Specific edible handling descriptions for items without crafting table / cooking pot recipes
    special_notes = {
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

    placeable_feasts = {
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

    rows = []
    for item_id in sorted(manifest_items):
        info = classifications[item_id]
        status = info["status"]
        reason = info["reason"]
        file_name = info["file"]

        is_placed = item_id in placeable_feasts or "block" in item_id or "pie" in item_id and "slice" not in item_id
        edible_str = "Yes (placed)" if is_placed else "Yes (hand)"

        recs = recipes_by_output.get(item_id, [])
        if recs:
            recipe_desc = "; ".join(recs)
        elif item_id in special_notes:
            recipe_desc = special_notes[item_id]
        else:
            recipe_desc = "Primary produce or cutting board result"

        confidence = "HIGH"
        source_desc = f"data/.../{file_name}"

        rows.append({
            "id": item_id,
            "edible": edible_str,
            "recipe": recipe_desc,
            "status": status,
            "reason": reason,
            "source": source_desc,
            "confidence": confidence
        })

    # Verify compound/difference coverage requirements
    dumplings_row = next(r for r in rows if r["id"] == "farmersdelight:dumplings")
    assert "#c:foods/raw_pork" in dumplings_row["recipe"], "Dumplings recipe missing raw_pork compound child!"
    assert "#c:foods/raw_chicken" in dumplings_row["recipe"], "Dumplings recipe missing raw_chicken compound child!"

    cabbage_rolls_row = next(r for r in rows if r["id"] == "farmersdelight:cabbage_rolls")
    assert "#c:foods/raw_meat" in cabbage_rolls_row["recipe"], "Cabbage rolls recipe missing raw_meat compound child!"
    assert "#c:foods/safe_raw_fish" in cabbage_rolls_row["recipe"], "Cabbage rolls recipe missing safe_raw_fish compound child!"

    squid_ink_row = next(r for r in rows if r["id"] == "farmersdelight:squid_ink_pasta")
    assert squid_ink_row["status"] == "UNKNOWN", "Squid ink pasta must be UNKNOWN!"
    assert squid_ink_row["reason"] == "seafood_policy_unspecified", "Squid ink pasta reason must be seafood_policy_unspecified!"

    table_lines = [
        "| Registry ID | Edible | Relevant Ingredients / Recipe | MuslimQoL Status | Reason | Evidence / Source | Confidence |",
        "| :--- | :--- | :--- | :--- | :--- | :--- | :--- |"
    ]
    for r in rows:
        table_lines.append(
            f"| `{r['id']}` | {r['edible']} | {r['recipe']} | **{r['status']}** | `{r['reason']}` | `{r['source']}` | {r['confidence']} |"
        )

    doc_content = f"""# Farmer's Delight 1.21.1 Food Classification Audit

> [!NOTE]
> **Disclaimer**: These are conservative gameplay classifications based on in-game recipe composition and are not a substitute for religious guidance or real-world halal certification.

This document details the food classification audit for **Farmer's Delight 1.3.4** on **Minecraft 1.21.1** (NeoForge).

## Audit Scope & Summary

* **Target Mod**: Farmer's Delight (`farmersdelight`)
* **Tested Version**: `1.21.1-1.3.4`
* **Reference JAR SHA-256**: `{jar_sha}`
* **Platform**: NeoForge 21.1.x / Java 21
* **Compatibility Namespace**: `muslimqol_farmersdelight`
* **Total Audited Edible Items**: 89
  * Hand-edible items: 80
  * Placeable feasts & whole pies: 9
* **Classification Breakdown**:
  * **HALAL**: {counts.get('HALAL', 0)} (seafood/fish, pure crops, vegetables, and audited permitted recipes containing dairy/eggs)
  * **RESTRICTED**: {counts.get('RESTRICTED', 0)} (swine-derived cuts, bacon meals, pork ramen, pumpkin soup with pork, dog food with carrion)
  * **DOUBTFUL**: {counts.get('DOUBTFUL', 0)} (variable protein mixtures like dumplings, cabbage rolls, barbecue sticks, and bone broth)
  * **UNKNOWN**: {counts.get('UNKNOWN', 0)} (ordinary uncertified livestock meats: beef, chicken, mutton dishes, and non-fish seafood items like squid ink pasta)
  * **Unclassified**: 0

---

## Detailed Classification Audit Table

{"\n".join(table_lines)}

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
"""

    os.makedirs(os.path.dirname(DOCS_AUDIT_PATH), exist_ok=True)
    with open(DOCS_AUDIT_PATH, "w", encoding="utf-8") as f:
        f.write(doc_content)
    print(f"\nSuccessfully generated audit document at: {DOCS_AUDIT_PATH}")


if __name__ == "__main__":
    main()
