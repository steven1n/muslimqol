# MuslimQoL Compatibility Audit Engine

> [!IMPORTANT]
> **The audit engine does not determine religious rulings and does not automatically publish MuslimQoL food classifications.**
>
> The Compatibility Audit Engine is an offline, deterministic diagnostic tool designed to help human auditors inspect third-party food mod JARs, discover items and recipes, flag ambiguities, and validate curated compatibility packs. All outputs are strictly diagnostic hints and evidence facts.

---

## 1. Architectural Role and Boundaries

```
Third-Party Mod JAR
        │
        ▼
Compatibility Audit Engine (Python, offline & deterministic)
  ├── 1. Registry Bytecode & Model Extraction
  ├── 2. Tag Hierarchy Resolution
  ├── 3. Nested Recipe Traversal (Compound / Difference)
  ├── 4. Tokenization & Keyword Heuristics
  ├── 5. Evidence Conflict & Divergence Diagnostics
  └── 6. Pack Validation against Curated JSONs
        │
        ▼
Diagnostic Reports (`summary.json`, `evidence.json`, `review.md`)
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
- Heuristic suggestions (`HIGH_RISK_RESTRICTED`, `MEAT_PROVENANCE_REQUIRED`, `LIKELY_PLANT_BASED`, `SEAFOOD_REVIEW`, `AMBIGUOUS_RECIPE`, `GENERAL_REVIEW`, `NO_SIGNAL`) are diagnostic categories, completely distinct from runtime `FoodStatus` (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`).

---

## 2. What Heuristics Do and Do NOT Do

### What Heuristics Do
- **Deterministic Tokenization**: Splits registry ID paths into canonical tokens across `snake_case`, `kebab-case`, `camelCase`, and compound phrases.
- **Recipe Traversal**: Recursively parses vanilla crafting, smelting, smoking, and NeoForge compound (`neoforge:compound`) and difference (`neoforge:difference`) structures.
- **Evidence Fact Assembly**: Gathers objective facts (`NAME_KEYWORD`, `NAME_EXCEPTION`, `RECIPE_ITEM`, `RECIPE_TAG`, `RECIPE_VARIABLE`, `ITEM_TAG`).
- **Conflict Detection**: Detects and highlights contradictions between name suggestions and ingredient compositions.
- **Prioritized Human Review**: Assigns a non-religious `review_priority` (10 to 100) to order items for auditor inspection.
- **Pack Verification**: Compares discovered candidate items against declared MuslimQoL pack rules to identify missing or extra items.

### What Heuristics Do NOT Do
- **Never publish religious classifications**: Output suggestions never write or alter production classification JSONs automatically.
- **Never approve items as HALAL solely by name**: Innocent or plant-like names (`apple_pie`, `grandmas_stew`) never bypass recipe verification.
- **Never trust self-declared religious labels**: Tokens like `halal`, `kosher`, or `permissible` are flagged as `UNTRUSTED_SELF_DESCRIPTION` and ignored as proof of permissibility.
- **Never call remote APIs or LLMs**: The tool is 100% offline, reproducible, explainable, and deterministic.

---

## 3. The Adversarial-Name Problem

> **Registry names are hints supplied by mod authors, not trusted ingredient provenance.**

In Minecraft modding, item registry IDs reflect developer naming preferences, cultural idioms, or fantasy lore rather than verifiable ingredient lists. Auditors face four major categories of name deception:

1. **Adversarial / Malicious Names (`halal_pork`)**:
   - A mod author might name an item `evilmod:halal_pork`.
   - Naive string matching or self-description trusting would mark it permissible.
   - The engine flags `halal` as `UNTRUSTED_SELF_DESCRIPTION` and surfaces an `UNTRUSTED_RELIGIOUS_CLAIM` conflict, keeping the item prioritized at `HIGH_RISK_RESTRICTED`.

2. **Innocent Names Concealing Forbidden Ingredients (`grandmas_stew`, `pumpkin_soup`)**:
   - `farmersdelight:pumpkin_soup` and `farmersdelight:noodle_soup` sound purely plant-based or neutral.
   - Their official recipes mandate `#c:foods/raw_pork`.
   - The engine detects `PLANT_NAME_MEAT_RECIPE` conflict with **HIGH** severity and assigns review priority 100.

3. **Qualified Meat Names (`vegan_ham`, `mock_pork`, `porkless_sausage`)**:
   - An item named `vegan_ham` contains the keyword `ham`, but has the qualifier `vegan`.
   - Treating `vegan_ham` as pork without inspection is a false positive.
   - The engine produces `NAME_EXCEPTION` alongside `NAME_KEYWORD`, raises `NAME_QUALIFIER_CONTRADICTION`, and routes it to `GENERAL_REVIEW` rather than auto-restricting it.

4. **Species Modifier Discrepancies (`turkey_bacon`)**:
   - `bacon` is traditionally swine-associated, but `turkey` modifies the meat origin.
   - `turkey_bacon` is avian poultry, requiring slaughter provenance rather than swine prohibition.
   - The engine recognizes `turkey` as a species modifier, routing the item to `MEAT_PROVENANCE_REQUIRED`.

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

Review priority ranges from 10 (lowest audit need) to 100 (immediate audit attention):

| Priority | Trigger Criteria | Example |
| :---: | :--- | :--- |
| **100** | Direct mandatory pork/swine recipe ingredient or tag | `pumpkin_soup`, `bacon` |
| **95** | Untrusted religious claim on prohibited item | `halal_pork` |
| **90** | Recipe vs Name contradiction | `noodle_soup` (plant name, pork recipe) |
| **85** | Unqualified swine name without recipe | `pork_chop_item` |
| **80** | Variable recipe with divergent fillings | `dumplings`, `cabbage_rolls` |
| **70** | Livestock / poultry meat requiring slaughter provenance | `beef_stew`, `chicken_cuts`, `mutton_wrap` |
| **60** | Intoxicant / alcohol keyword review | `apple_cider`, `beer`, `wine` |
| **50** | Non-fish seafood review (cephalopods, shellfish) | `squid_ink_pasta`, `crab_legs` |
| **40** | Qualified name / general review | `vegan_ham`, `porkless_sausage` |
| **30** | Verified plant-based, dairy, egg, or scaled fish | `apple_pie`, `tomato`, `cod_slice` |
| **10** | No recognizable signals found | `unnamed_produce` |

---

## 6. CLI Usage & Workflow

### Generic Invocations

```bash
# Standard module invocation
python3 -m tools.compatibility.audit.cli \
  --jar /path/to/mod.jar \
  --mod-id <mod_id> \
  --pack <path_to_muslimqol_pack> \
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

### Generated Artifacts
All files are generated under `build/audit/<mod_id>/` (automatically gitignored):
- **`summary.json`**: High-level counts, SHA-256 hash, suggestion breakdown, and pack validation results.
- **`evidence.json`**: Full per-item trace of all discovered facts, weights, recipes, and existing pack statuses.
- **`review.md`**: Human-readable prioritized report grouping items by risk and highlighting conflicts.

---

## 7. Python Test Suite

Run all unit tests with Python's standard `unittest`:

```bash
python3 -m unittest discover \
  -s tools/compatibility/tests \
  -p 'test_*.py'
```
