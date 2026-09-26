# MuslimQoL Compatibility Audit Engine

> [!IMPORTANT]
> **The audit engine does not determine religious rulings and does not automatically publish MuslimQoL food classifications.**
>
> The Compatibility Audit Engine is an offline, deterministic diagnostic tool designed to help human auditors inspect third-party food mod JARs, discover items and recipes, flag ambiguities, and validate curated compatibility packs. All outputs are strictly diagnostic hints and evidence facts.
>
> **Review Priority Semantics**: The numeric priority scale (10–100) reflects **audit urgency and review triage order**, NOT theological severity.

---

## 1. Architectural Role and Boundaries

```
Third-Party Mod JAR
        │
        ▼
Compatibility Audit Engine (Python, offline & deterministic)
  ├── 1. Registry Bytecode & Model Extraction
  ├── 2. Tag Hierarchy Resolution (Token-Safe Matching)
  ├── 3. Nested Recipe Traversal (Compound / Difference)
  ├── 4. Recipe Provenance Graph (Recursive Tracing, Cycles, Depth Limit)
  ├── 5. Tokenization & Keyword Heuristics
  ├── 6. Evidence Conflict & Divergence Diagnostics
  ├── 7. Parser Diagnostics & Error Reporting
  └── 8. Pack Validation against Curated JSONs (Clean & Unknown ID Checks)
        │
        ▼
Diagnostic Reports (`summary.json`, `evidence.json`, `provenance.json`, `review.md`)
        │
        ▼
Human Auditor Review & Jurisprudential Verification
        │
        ▼
Authoritative MuslimQoL Compatibility Pack (`muslimqol/food_classifications/*.json`)
        │
        ▼
Runtime Resolver (`USER_OVERRIDE > DATAPACK > ITEM_TAG > BUILTIN > UNKNOWN`)
```

The runtime Minecraft / NeoForge architecture is completely decoupled from the audit engine:
- The engine runs offline in Python and does not introduce any runtime dependencies.
- Production Java code (`FoodClassifier`, `FoodCompatibilityManager`, `FoodClassificationRegistry`) remains unchanged.
- Heuristic suggestions (`HIGH_RISK_RESTRICTED`, `AMBIGUOUS_RECIPE`, `MEAT_PROVENANCE_REQUIRED`, `SEAFOOD_REVIEW`, `FISH_REVIEW_BASELINE`, `LIKELY_LOW_RISK_RECIPE`, `LIKELY_PLANT_BASED`, `GENERAL_REVIEW`, `NO_SIGNAL`) are diagnostic categories, completely distinct from runtime `FoodStatus` (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`).

---

## 2. What Heuristics Do and Do NOT Do

### What Heuristics Do
- **Token-Safe Semantic Matching**: Splits registry ID paths and tag names into canonical tokens, eliminating substring false positives (e.g. `chamomile_tea` and `hamburger` do not trigger `ham` swine signals).
- **Recipe Traversal**: Recursively parses vanilla crafting, smelting, smoking, and NeoForge compound (`neoforge:compound`) and difference (`neoforge:difference`) structures.
- **Evidence Fact Assembly**: Gathers objective facts (`NAME_KEYWORD`, `NAME_EXCEPTION`, `RECIPE_ITEM`, `RECIPE_TAG`, `RECIPE_VARIABLE`, `ITEM_TAG`).
- **Semantic Separation**: Distinguishes pure plant-based foods (`LIKELY_PLANT_BASED`) from dairy/egg composites (`LIKELY_LOW_RISK_RECIPE`) and scaled fish (`FISH_REVIEW_BASELINE`).
- **Conflict Detection**: Detects and highlights contradictions between name suggestions and ingredient compositions.
- **Parser Diagnostics**: Emits structured `ParseDiagnostic` objects for corrupted JSONs, malformed recipes, or unknown tags.
- **Pack Verification**: Compares discovered candidate items against declared MuslimQoL pack rules to identify missing, extra, duplicate, or unknown registry IDs, computing a boolean `clean` indicator.

### What Heuristics Do NOT Do
- **Never publish religious classifications**: Output suggestions never write or alter production classification JSONs automatically.
- **Never approve items as HALAL solely by name**: Innocent or plant-like names (`apple_pie`, `grandmas_stew`) never bypass recipe verification.
- **Never trust self-declared religious labels**: Tokens like `halal`, `kosher`, or `permissible` are flagged as `UNTRUSTED_SELF_DESCRIPTION` and ignored as proof of permissibility.
- **Never call remote APIs or LLMs**: The tool is 100% offline, reproducible, explainable, and deterministic.

---

## 3. The Adversarial-Name Problem & Substring Traps

> **Registry names are hints supplied by mod authors, not trusted ingredient provenance.**

In Minecraft modding, item registry IDs reflect developer naming preferences, cultural idioms, or fantasy lore rather than verifiable ingredient lists. Auditors face four major categories of name deception:

1. **Substring Collisions (Solved by Token-Safe Matching)**:
   - Naive string matching (`"ham" in item_id`) causes severe false positives:
     - `chamomile_tea` contains `"ham"`, but its tokens are `["chamomile", "tea"]`.
     - `hamburger` contains `"ham"`, but its token is `["hamburger"]` (an ambiguous processed meat, not pork).
   - The engine uses `tokenize_identifier()` across namespaces, slashes, underscores, and camelCase to prevent substring entrapment.

2. **Adversarial / Malicious Names (`halal_pork`)**:
   - A mod author might name an item `evilmod:halal_pork`.
   - Naive string matching or self-description trusting would mark it permissible.
   - The engine flags `halal` as `UNTRUSTED_SELF_DESCRIPTION` and surfaces an `UNTRUSTED_RELIGIOUS_CLAIM` conflict, keeping the item prioritized at `HIGH_RISK_RESTRICTED`.

3. **Innocent Names Concealing Forbidden Ingredients (`pumpkin_soup`, `noodle_soup`)**:
   - `farmersdelight:pumpkin_soup` and `farmersdelight:noodle_soup` sound purely plant-based or neutral.
   - Their official recipes mandate `#c:foods/raw_pork`.
   - The engine detects `PLANT_NAME_MEAT_RECIPE` conflict with **HIGH** severity and assigns review priority 100.

4. **Qualified Meat Names (`vegan_ham`, `mock_pork`, `porkless_sausage`)**:
   - An item named `vegan_ham` contains the keyword `ham`, but has the qualifier `vegan`.
   - The engine produces `NAME_EXCEPTION` alongside `NAME_KEYWORD`, raises `NAME_QUALIFIER_CONTRADICTION`, and routes it to `GENERAL_REVIEW` rather than auto-restricting it.

---

## 4. Evidence Hierarchy

When conflicting facts arise during an audit, the engine evaluates evidence in a strict hierarchy:

$$\text{Mandatory Recipe Ingredients} > \text{Tag Classifications} > \text{Registry Path Keywords} > \text{Self-Declared Religious Claims}$$

| Evidence Tier | Source | Example | Authority Level |
| :--- | :--- | :--- | :--- |
| **Tier 1: Explicit Recipe** | Recipe JSON | `minecraft:porkchop` in ingredients | **Definitive**: Overrules name claims. |
| **Tier 2: Item Tags** | `data/c/tags/item/` | `#c:foods/cooked_pork` | **High**: Authoritative mod metadata. |
| **Tier 3: Name Keywords** | Registry ID path | `pork`, `beef`, `cabbage` | **Heuristic Hint**: Requires ingredient audit. |
| **Tier 4: Self-Descriptions** | Registry ID path | `halal`, `kosher`, `zabiha` | **Untrusted**: Diagnostic warning only. |

---

## 5. Review Priority Scale

Review priority ranges from 10 (lowest audit need) to 100 (immediate audit attention), ordering items strictly by **audit urgency**:

| Priority | Category / Criteria | Description & Example |
| :---: | :--- | :--- |
| **100** | Mandatory Swine Ingredient or Tag | Direct pork/bacon/ham ingredients or tags (`bacon`, `pumpkin_soup`). |
| **90–95** | High-Risk Contradiction / Conflict | Untrusted claims (`halal_pork`) or plant name hiding pork (`noodle_soup`). |
| **80–85** | Variable Swine Recipes / Unqualified Names | Divergent fillings (`dumplings`, `cabbage_rolls`) or pork name without recipe (`ham`). |
| **60–70** | Meat Provenance Required | Livestock/poultry meat needing slaughter verification (`beef_stew`, `chicken_cuts`). |
| **50** | Seafood Review | Non-fish marine items requiring jurisprudence review (`squid_ink_pasta`, `crab_legs`). |
| **40** | Scaled Fish Review Baseline | Scaled fish or fish-derived ingredients (`cod_slice`, `salmon_slice`, `fish_stew`). |
| **30** | Likely Low-Risk Recipes | Dairy, egg, honey, or permitted composite items (`fried_egg`, `hot_cocoa`, `milk_bottle`). |
| **20** | Likely Plant-Based Baseline | Pure crop, vegetable, fruit, grain, fungal items (`apple_pie`, `tomato`, `cabbage`). |
| **10** | No Signal Discovered | Items lacking recipes, identifiable tags, or recognizable keywords (`unnamed_produce`). |

---

## 6. Recipe Provenance Graph (v0.2)

### 6.1 Multi-Hop Transitive Provenance vs. Direct Ingredients
In complex culinary mods, food recipes rarely call for raw base ingredients directly. Instead, recipes chain through custom intermediate foods, butchered meats, and multi-tier tag hierarchies:
$$\text{final food} \longrightarrow \text{intermediate food} \longrightarrow \text{recipe} \longrightarrow \text{tag} \longrightarrow \text{nested tag} \longrightarrow \text{base ingredient}$$

The Recipe Provenance Graph (`RecipeProvenanceEngine`) recursively traces ingredient lineage down to concrete items and vanilla references. For example:
- `pamhc2foodcore:hotdogitem` $\to$ `pamhc2foodcore:groundporkitem` $\to$ `#c:rawpork` $\to$ `minecraft:porkchop` (**Transitive Swine Verified**)
- `pamhc2foodcore:chickendinneritem` $\to$ `pamhc2foodcore:friedchickenitem` $\to$ `#c:rawchicken` $\to$ `minecraft:chicken` (**Transitive Poultry Verified**)

### 6.2 Mandatory vs. Alternative (Variable) Branch Semantics
A fundamental principle of the provenance graph is distinguishing **mandatory** ingredients from **alternative** choices:
- **Mandatory Ingredient**: An ingredient slot that requires swine/meat across all valid substitutions.
  - Example: `hotdogitem` mandates `groundporkitem`. Every valid substitution is pork.
  - Evaluation: Flagged as `HIGH_RISK_RESTRICTED` (priority 100), paths labeled `Mandatory transitive swine provenance`.
- **Alternative / Variable Branch**: An ingredient slot or tag providing multiple choices where some are swine/meat and others are plant, fish, or halal meat.
  - Example: `farmersdelight:dumplings` accepts chicken, beef, or pork.
  - Example: `pamhc2foodcore:stockitem` accepts `#c:stock_ingredients` (pork, beef, chicken, or fish).
  - Evaluation: Flagged as `AMBIGUOUS_RECIPE` (priority 80–90) with `VARIABLE_PROVENANCE` evidence; individual alternative paths are explicitly marked `mandatory=False` and labeled `Alternative transitive ... provenance`.

### 6.3 Dairy and Egg Provenance Separation
`is_pure_plant` strictly requires that all consumed dietary ingredient provenance is demonstrably plant/fungal/crop-based. Foods containing milk, butter, cheese, or eggs (e.g. `apple_pie`, `custard`, `fried_egg`, `milk_cookie`) are tracked with dedicated dairy/egg provenance dimensions (`can_dairy_egg`, `mandatory_dairy_egg`, `variable_dairy_egg`) and route to `LIKELY_LOW_RISK_RECIPE` (priority 30), never falsely claimed as pure plant.

### 6.4 Tool & Container Exclusion Policy
Crafting recipes often include preparation tools, cooking surfaces, or packaging containers (e.g. `c:tools/knife`, `c:tools/skillet`, `c:tools/pot`, `minecraft:bowl`, `minecraft:bucket`, `minecraft:stick`).
The engine automatically filters out tools and utility containers from ingredient evaluation so kitchen utensils never distort dietary classification.

### 6.5 Cycle Detection, Incomplete Provenance & Depth Limits
Minecraft crafting frequently introduces cycles (e.g. crate packing/unpacking `cabbage <-> cabbage_crate`, pie slicing `apple_pie <-> apple_pie_slice`, cutting `cabbage <-> cabbage_leaf`, and liquid bottling `milk_bucket <-> milk_bottle`):
- **Cycle Semantics (`PROVENANCE_CYCLE`)**: A cyclic edge is **non-evidentiary / incomplete**, never a safe alternative path. Cyclic branches cannot turn mandatory swine into variable swine. Where all recipes in a JAR are reciprocal conversions (e.g. `cabbage <-> cabbage_crate`), the engine resolves the item to its inherent recognized identity.
- **Incomplete Provenance Model**: Traversal truncations (depth limits or cycles without safe paths) represent **UNKNOWN**, never proof of safety. If mandatory risk is present, the mandatory risk takes precedence; otherwise, incomplete items safely route to `GENERAL_REVIEW` (review priority 65), never `LIKELY_PLANT_BASED`.
- **Depth Limits (`PROVENANCE_DEPTH_LIMIT`)**: Bounded by `--max-provenance-depth` (default 8) to prevent runaway execution in deep crafting webs.
- **Per-Item Diagnostics**: `cycles_detected` and `depth_limits_hit` in `ItemProvenance` report per-item counts, while engine-wide totals are recorded in `summary.json`.

### 6.6 Lattice Propagation & Triage Integration
Provenance facts integrate into the audit lattice:
$$\text{Mandatory Swine (100)} > \text{Variable Swine / Ambiguous (80--90)} > \text{Mandatory Meat (70)} > \text{Incomplete Provenance (65)} > \text{Fish Baseline (40)} > \text{Dairy/Low-Risk (30)} > \text{Pure Plant (20)}$$

---

## 7. CLI Usage & Workflow

### Generic Invocations

```bash
# Standard module invocation
python3 -m tools.compatibility.audit.cli \
  --jar /path/to/mod.jar \
  --mod-id <mod_id> \
  --pack <path_to_muslimqol_pack> \
  --max-provenance-depth 8 \
  --output build/audit/<mod_id>

# Or via wrapper script
python3 tools/compatibility/audit_mod.py \
  --jar /path/to/mod.jar \
  --mod-id <mod_id> \
  --pack <path_to_muslimqol_pack> \
  --output build/audit/<mod_id>
```

### Golden Reference: Farmer's Delight 1.3.4

```bash
python3 -m tools.compatibility.audit.cli \
  --jar /Users/akiyama/.gemini/antigravity/brain/50f5d25e-a862-4407-af5a-5359a03241ce/scratch/FarmersDelight-1.21.1-1.3.4.jar \
  --mod-id farmersdelight \
  --pack src/main/resources/data/muslimqol_farmersdelight \
  --output build/audit/farmersdelight
```

Output highlights:
- **Total Registry Items Discovered**: 185
- **Edible Candidates Audited**: 89
- **Curated Pack Validated**: `muslimqol_farmersdelight`
  - Clean: **True**
  - Classified in pack: 89
  - Missing: 0, Extra: 0, Duplicates: 0, Unknown IDs: 0, Diagnostics: 0
- **Provenance Graph**: 33 transitive items, 4 variable provenance items, 30 cyclic edges handled, 0 depth limits hit.

### Generated Artifacts
All files are generated under `build/audit/<mod_id>/` (automatically gitignored):
- **`summary.json`**: High-level counts, SHA-256 hash, suggestion breakdown, pack validation report, provenance summary, and parser diagnostics.
- **`evidence.json`**: Full per-item trace of all discovered facts, weights, recipes, provenance trees, and existing pack statuses.
- **`provenance.json`**: Dedicated JSON file detailing all recursive provenance paths, alternative branches, and rendered trees for every edible candidate.
- **`review.md`**: Human-readable prioritized report grouping items by audit urgency, detailing conflicts, displaying parser diagnostics, and including full ASCII Recipe Provenance Trees.

---

## 8. Python Test Suite

Run all unit tests with Python's standard `unittest`:

```bash
python3 -m unittest discover \
  -s tools/compatibility/tests \
  -p 'test_*.py'
```
