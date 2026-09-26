# Recipe Provenance Graph v0.2: Cross-Mod Validation Report

## 1. Executive Summary & Validation Scope

This validation report evaluates **MuslimQoL Compatibility Audit Engine v0.2: Recipe Provenance Graph** across two major, architecturally distinct Minecraft 1.21.1 food mods:
1. **Farmer's Delight 1.3.4** (`FarmersDelight-1.21.1-1.3.4.jar`)
2. **Pam's HarvestCraft 2 - Food Core 1.0.4** (`pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar`)

### Background & Objective
In Compatibility Audit Engine v0.1, recipe analysis was strictly **single-hop / direct-ingredient** matching. If an item used custom intermediate items (e.g. `groundporkitem`, `friedchickenitem`, `pie_crust`) or nested tag hierarchies (e.g. `#c:foods/raw_meat` -> `#c:foods/raw_pork` -> `minecraft:porkchop`), the v0.1 engine could only evaluate immediate direct ingredients or fall back to registry identifier tokens.

Audit Engine v0.2 implements a deterministic **Recipe Provenance Graph** that recursively traces:
$$\text{final food} \longrightarrow \text{custom intermediate item} \longrightarrow \text{recipe} \longrightarrow \text{tag} \longrightarrow \text{nested tag} \longrightarrow \text{concrete ingredient}$$
while strictly preserving **alternative-choice semantics** (Mandatory vs. Variable branches).

### Key Architectural Invariants & Safety Guarantees
- **Offline & Diagnostic Only**: The engine executes completely offline in Python. **Zero production Java code** is added or modified.
- **Decoupled from Runtime**: Output suggestions remain diagnostic hints for human auditors; they are never connected to the runtime `FoodClassifier`.
- **No Automatic Pack Generation**: No compatibility pack for Pam's HarvestCraft 2 is generated (`data/muslimqol_pams/` is strictly avoided).
- **Adversarial & Ambiguity Catch**: Successfully detects hidden swine pathways in innocent-sounding recipes (e.g. vegetable soups using stock made with pork) and properly flags them for human review.

---

## 2. Target Mod Forensics & Provenance Metrics

### 2.1 Artifact Summary

| Property | Farmer's Delight | Pam's HarvestCraft 2 Food Core |
| :--- | :--- | :--- |
| **Artifact Filename** | `FarmersDelight-1.21.1-1.3.4.jar` | `pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar` |
| **Mod ID** | `farmersdelight` | `pamhc2foodcore` |
| **Mod Version** | `1.3.4` | `1.0.4` |
| **Minecraft Version** | `1.21.1` | `1.21.1` |
| **Mod Loader** | NeoForge `[21.1.0,)` | NeoForge `[21.1.0,)` |
| **SHA-256 Checksum** | `139ad7696462c89c03eea463f805abffa552526c5dadaadae221dd9624cb197c` | `acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9` |

### 2.2 Provenance Graph Execution Metrics

| Provenance Metric | Farmer's Delight 1.3.4 | Pam's HarvestCraft 2 Food Core 1.0.4 |
| :--- | :---: | :---: |
| **Total Discovered Items** | 185 | 202 |
| **Edible Candidates** | 89 | 180 |
| **Parsed Recipes** | 328 | 218 |
| **Parsed Item Tags** | 105 | 197 |
| **Items with Transitive Evidence** | **33** | **62** |
| **Items with Variable Provenance** | **4** | **14** |
| **Distinct Cyclic Edges Detected** | **30** | **0** |
| **Depth Limits Hit (Max Depth = 8)** | **0** | **3** |
| **Curated Pack Clean Status** | **True** (89 / 89 classified) | N/A (Pack not yet built) |

---

## 3. Comparative Distribution: v0.1 vs. v0.2

### 3.1 Farmer's Delight 1.3.4 Comparison

Farmer's Delight served as the regression baseline. All 89 edible candidates retained their exact heuristic categorization between v0.1 and v0.2, while gaining deep ingredient lineage and cycle tracking:

| Suggestion Category | v0.1 Count | v0.2 Count | Delta |
| :--- | :---: | :---: | :---: |
| `HIGH_RISK_RESTRICTED` | 10 | 10 | 0 |
| `MEAT_PROVENANCE_REQUIRED` | 19 | 19 | 0 |
| `LIKELY_PLANT_BASED` | 31 | 31 | 0 |
| `LIKELY_LOW_RISK_RECIPE` | 12 | 12 | 0 |
| `FISH_REVIEW_BASELINE` | 10 | 10 | 0 |
| `SEAFOOD_REVIEW` | 1 | 1 | 0 |
| `AMBIGUOUS_RECIPE` | 6 | 6 | 0 |
| `GENERAL_REVIEW` | 0 | 0 | 0 |
| `NO_SIGNAL` | 0 | 0 | 0 |
| **Total Edible Items** | **89** | **89** | **0** |

- **Pack Verification**: `muslimqol_farmersdelight` verified **Clean = True**, 0 missing, 0 extra, 0 duplicates, 0 unknown IDs, 0 diagnostics.
- **Variable Provenance (4 items)**: `cabbage_rolls`, `dumplings`, `barbecue_stick`, `dog_food` were confirmed to have variable meat/swine recipe branches, matching human curation in the pack (`DOUBTFUL:unknown_ingredients`).

### 3.2 Pam's HarvestCraft 2 Food Core Comparison

In Pam's HarvestCraft 2, the recursive provenance graph produced profound diagnostic enhancements by discovering transitive meat/swine ingredients hidden inside custom intermediate foods and tags:

| Suggestion Category | v0.1 Count | v0.2 Count | Delta | Diagnostic Rationale |
| :--- | :---: | :---: | :---: | :--- |
| `HIGH_RISK_RESTRICTED` | 13 | 13 | 0 | Retained 100% precision on swine items. |
| `MEAT_PROVENANCE_REQUIRED` | 27 | 31 | **+4** | Intermediate meats (`friedchickenitem`, `groundchickenitem`) traced to concrete poultry/meat. |
| `LIKELY_PLANT_BASED` | 95 | 89 | **-6** | Innocently named vegetable/noodle soups utilizing meat-derived stock shifted to ambiguous. |
| `LIKELY_LOW_RISK_RECIPE` | 37 | 28 | **-9** | Multi-ingredient soups and composite dishes utilizing stock shifted to ambiguous. |
| `FISH_REVIEW_BASELINE` | 6 | 6 | 0 | Unchanged fish baseline items. |
| `SEAFOOD_REVIEW` | 0 | 0 | 0 | No marine/crustacean items in Food Core. |
| `AMBIGUOUS_RECIPE` | 2 | 13 | **+11** | Soups using `pamhc2foodcore:stockitem` inherited its variable swine/meat provenance. |
| `GENERAL_REVIEW` | 0 | 0 | 0 | No qualified-name contradictions. |
| `NO_SIGNAL` | 0 | 0 | 0 | All items had bytecode or tag signals. |
| **Total Edible Items** | **180** | **180** | **0** | |

---

## 4. Deep Dive: The Stock Item Ambiguity Breakthrough

One of the most critical safety discoveries of the v0.2 Recipe Provenance Graph occurred in Pam's HarvestCraft 2 soups.

### The Problem in v0.1
In v0.1, the engine evaluated:
- `pamhc2foodcore:vegetablenoodlesoupitem`
- `pamhc2foodcore:carrotsoupitem`
- `pamhc2foodcore:potatosoupitem`
- `pamhc2foodcore:pumpkinsoupitem`
- `pamhc2foodcore:noodlesoupitem`

In v0.1, their recipes called for vegetables (`#c:crops/carrot`, `#c:crops/potato`, `#c:crops/pumpkin`), pasta (`#c:pasta`), and `#c:stock`. Because the tag name `#c:stock` contains no swine keywords, v0.1 classified these soups as `LIKELY_PLANT_BASED` (priority 20) or `LIKELY_LOW_RISK_RECIPE` (priority 30). An auditor without recipe inspection might easily assume these soups are purely vegetarian.

### The v0.2 Provenance Resolution
The v0.2 engine recursively resolves `#c:stock`:
1. `#c:stock` resolves to `pamhc2foodcore:stockitem`.
2. `pamhc2foodcore:stockitem` has a recipe combining `potitem` (tool), `freshwateritem`, vegetables, and `#c:stock_ingredients` / `#c:rawmeats`.
3. `#c:rawmeats` expands into alternative branches:
   - `#c:rawpork` -> `minecraft:porkchop` (**SWINE**)
   - `#c:rawbeef` -> `minecraft:beef` (**MEAT**)
   - `#c:rawchicken` -> `minecraft:chicken` (**MEAT**)
   - `#c:rawmutton` -> `minecraft:mutton` (**MEAT**)
   - `#c:rawfish` -> `minecraft:cod` (**FISH**)
4. Because the player can craft `stockitem` using pork, beef, chicken, or fish:
   $$\text{mandatory\_swine} = \text{False}, \quad \text{variable\_swine} = \text{True}$$
5. The variable swine property propagates up to any dish using `stockitem`.
6. Result: All five soups are elevated to **`AMBIGUOUS_RECIPE`** with review priority **80**.

Human auditors are immediately alerted that in Pam's HarvestCraft 2, ordering or crafting "vegetable soup" may involve stock prepared with pork!

---

## 5. Concrete Provenance Trees

Below are verbatim ASCII provenance trees generated by `RecipeProvenanceEngine` demonstrating key transitive and variable patterns.

### 5.1 Mandatory Transitive Swine: `pamhc2foodcore:hotdogitem`
```text
pamhc2foodcore:hotdogitem
└── pamhc2foodcore:groundporkitem (SWINE ⚠)
    └── #c:rawpork (SWINE ⚠)
        └── #c:rawpork/rawpork (SWINE ⚠)
            └── minecraft:porkchop (SWINE ⚠)
```
- **Evaluation**: The hotdog requires `groundporkitem`, which requires `#c:rawpork`, which resolves to `minecraft:porkchop`.
- **Suggestion**: `HIGH_RISK_RESTRICTED` (Review Priority 100).
- **Semantics**: Mandatory Swine ingredient verified through 3 hops of recipe and tag nesting.

### 5.2 Mandatory Transitive Poultry: `pamhc2foodcore:chickendinneritem`
```text
pamhc2foodcore:chickendinneritem
└── pamhc2foodcore:friedchickenitem (TRANSITIVE)
    └── #c:rawchicken (MEAT)
        └── #c:rawchicken/rawchicken (MEAT)
            └── minecraft:chicken (MEAT)
```
- **Evaluation**: Traced through intermediate prepared item `friedchickenitem` to raw chicken.
- **Suggestion**: `MEAT_PROVENANCE_REQUIRED` (Review Priority 70).
- **Semantics**: Mandatory livestock/poultry meat confirmed without any swine contamination.

### 5.3 Multi-Branch Variable Recipe: `farmersdelight:cabbage_rolls`
```text
farmersdelight:cabbage_rolls [VARIABLE]
├── #c:foods/raw_meat (MEAT)
│   ├── #c:foods/raw_chicken (MEAT)
│   │   ├── minecraft:chicken (MEAT)
│   │   └── farmersdelight:chicken_cuts (MEAT)
│   │       └── minecraft:chicken (MEAT)
│   ├── #c:foods/raw_pork (SWINE ⚠)
│   │   ├── minecraft:porkchop (SWINE ⚠)
│   │   │   └── farmersdelight:ham (SWINE ⚠)
│   │   └── #c:foods/raw_bacon (SWINE ⚠)
│   │       └── farmersdelight:bacon (SWINE ⚠)
│   │           └── minecraft:porkchop (SWINE ⚠)
│   │               └── farmersdelight:ham (SWINE ⚠)
│   ├── #c:foods/raw_beef (MEAT)
│   │   ├── minecraft:beef (MEAT)
│   │   └── farmersdelight:minced_beef (MEAT)
│   │       └── minecraft:beef (MEAT)
│   └── #c:foods/raw_mutton (MEAT)
│       ├── minecraft:mutton (MEAT)
│       └── farmersdelight:mutton_chops (MEAT)
│           └── minecraft:mutton (MEAT)
└── #c:foods/safe_raw_fish (FISH)
    └── #c:foods/raw_fish (FISH)
        ├── #c:foods/raw_cod (FISH)
        │   ├── minecraft:cod (FISH)
        │   └── farmersdelight:cod_slice (FISH)
        │       └── minecraft:cod (FISH)
        └── #c:foods/raw_salmon (FISH)
            ├── minecraft:salmon (FISH)
            └── farmersdelight:salmon_slice (FISH)
                └── minecraft:salmon (FISH)
```
- **Evaluation**: The cooking pot recipe allows `#c:foods/raw_meat` OR `#c:foods/safe_raw_fish`. Swine is one of multiple possible alternatives.
- **Suggestion**: `AMBIGUOUS_RECIPE` (Review Priority 90 due to plant name with optional swine).
- **Semantics**: Preserves alternative semantics; swine is possible but NOT mandatory.

### 5.4 Multi-Branch Variable Recipe: `farmersdelight:dumplings`
```text
farmersdelight:dumplings [VARIABLE]
├── #c:foods/raw_chicken (MEAT)
│   ├── minecraft:chicken (MEAT)
│   └── farmersdelight:chicken_cuts (MEAT)
│       └── minecraft:chicken (MEAT)
├── #c:foods/raw_pork (SWINE ⚠)
│   ├── minecraft:porkchop (SWINE ⚠)
│   │   └── farmersdelight:ham (SWINE ⚠)
│   └── #c:foods/raw_bacon (SWINE ⚠)
│       └── farmersdelight:bacon (SWINE ⚠)
│           └── minecraft:porkchop (SWINE ⚠)
│               └── farmersdelight:ham (SWINE ⚠)
└── #c:foods/raw_beef (MEAT)
    ├── minecraft:beef (MEAT)
    └── farmersdelight:minced_beef (MEAT)
        └── minecraft:beef (MEAT)
```
- **Evaluation**: Dumpling meat fillings accept chicken, pork, or beef.
- **Suggestion**: `AMBIGUOUS_RECIPE` (Review Priority 90).

### 5.5 Innocuous Plant Composite with Cyclic Milk: `farmersdelight:apple_pie`
```text
farmersdelight:apple_pie
└── (No external dietary provenance paths)
```
- **Evaluation**: Apple pie uses `pie_crust` (wheat, sugar, milk) and apples. The reversible milk conversions (`milk_bucket <-> milk_bottle`) and pie slicing (`apple_pie <-> apple_pie_slice`) are cycle-filtered, correctly leaving 0 swine or meat signals.
- **Suggestion**: `LIKELY_PLANT_BASED` (Review Priority 20).

---

## 6. Cycle Detection & Graph Termination

Minecraft crafting recipes frequently contain cycles:
1. **Container / Liquid Packaging Cycles**:
   - `farmersdelight:milk_bottle` $\longleftrightarrow$ `minecraft:milk_bucket`
2. **Reversible Storage Blocks**:
   - `farmersdelight:cabbage` $\longleftrightarrow$ `farmersdelight:cabbage_crate`
   - `farmersdelight:tomato` $\longleftrightarrow$ `farmersdelight:tomato_crate`
3. **Cutting & Slicing Cycles**:
   - `farmersdelight:apple_pie` $\longleftrightarrow$ `farmersdelight:apple_pie_slice`
   - `farmersdelight:cabbage` $\longleftrightarrow$ `farmersdelight:cabbage_leaf`

### Cycle Detection Mechanism
- The engine maintains a call-stack of active items (`item_stack`) and active tags (`tag_stack`).
- When an edge `(source, target)` points to an element already on the traversal stack, a cycle is detected:
  - Traversal immediately terminates for that branch.
  - A structured `ParseDiagnostic` with `error_type="PROVENANCE_CYCLE"` is recorded.
  - A deduplicated set of seen cyclic edges prevents diagnostic log explosion.
- **Farmer's Delight Result**: Exactly **30 distinct cyclic edges** detected and safely bypassed; 0 infinite loops, 0 crashes.

---

## 7. Depth Limits & Deep Crafting Chains

In Pam's HarvestCraft 2, intermediate culinary chains can be very deep:
$$\text{wheat} \to \text{flour} \to \text{dough} \to \text{bread} \to \text{toast} \to \text{applejellytoast}$$

### Depth Limit Mechanism
- The `--max-provenance-depth` argument (default **8**) caps recursion depth.
- When `len(path_stack) >= max_depth`:
  - Recurse terminates safely.
  - A `ParseDiagnostic` with `error_type="PROVENANCE_DEPTH_LIMIT"` is emitted with the exact path stack.
- **Pam's HarvestCraft 2 Result**: Exactly **3 depth limits hit** at depth 8:
  1. `pamhc2foodcore:doughitem`: reached via `applejellytoastitem -> toast -> bread -> dough -> mixingbowlitem`
  2. `pamhc2foodcore:flouritem`: reached via `baconcheeseburgeritem -> bread -> dough -> flour -> grinderitem`
  3. `pamhc2foodcore:saltitem`: reached via `baconcheeseburgeritem -> bread -> dough -> salt -> freshwateritem`
- All 3 occurred inside purely plant/mineral intermediate ingredients where food safety is unaffected.

---

## 8. Unit Test Suite Coverage

A comprehensive test suite in `tools/compatibility/tests/test_provenance.py` covers all edge cases and graph semantics:

| Test Case | Scenario Verified | Status |
| :--- | :--- | :---: |
| `test_direct_ingredient_resolution` | Resolves single-hop item ingredients | **PASS** |
| `test_transitive_item_chain` | Traces multi-hop custom item chains | **PASS** |
| `test_tag_branch_expansion` | Expands nested tag branches | **PASS** |
| `test_alternative_choice_semantics` | Distinguishes mandatory vs alternative ingredients | **PASS** |
| `test_cycle_detection` | Detects and safely breaks cyclic recipes | **PASS** |
| `test_depth_limit` | Enforces recursion limits and emits diagnostics | **PASS** |
| `test_tool_container_exclusion` | Ignores knives, bowls, pots, and buckets | **PASS** |
| `test_tree_rendering` | Generates accurate hierarchical ASCII trees | **PASS** |
| `test_swine_tag_detection` | Identifies swine tags in nested hierarchies | **PASS** |
| `test_meat_tag_detection` | Identifies meat tags in nested hierarchies | **PASS** |
| `test_fish_tag_detection` | Identifies fish tags in nested hierarchies | **PASS** |
| `test_evidence_engine_integration` | Validates lattice suggestions with provenance facts | **PASS** |

### Test Suite Execution
```bash
python3 -m unittest discover tools/compatibility/tests/
........................................................
----------------------------------------------------------------------
Ran 56 tests in 0.021s

OK
```
All **56 unit tests pass** (exceeding the baseline of 44 tests).

---

## 9. Conclusion

The v0.2 Recipe Provenance Graph successfully bridges the gap between surface item names and deep ingredient reality. By systematically tracking alternative vs. mandatory branches, detecting cycles, and ignoring kitchen tools, it uncovers hidden culinary risks (such as stock-derived swine in soups) while preserving 100% precision on existing pack validations.
