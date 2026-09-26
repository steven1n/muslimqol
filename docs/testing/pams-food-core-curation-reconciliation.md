# Pam's HarvestCraft 2 Food Core Curation Reconciliation

> [!NOTE]
> **Artifact**: `pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar`  
> **SHA-256**: `acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9`  
> **Evaluation Depth**: 16 (reaches 0 incomplete items)  
> **Items Reconciled**: Exactly 180 edible items  

---

## 1. Resolution of the Historical 21 vs. 13 Discrepancy

In early v0.2 working drafts prior to PR #8, the audit report noted `HIGH_RISK_RESTRICTED = 21`, whereas the curated pack contains `RESTRICTED = 13`. This gap is completely reconciled across four distinct semantic categories:

### Group A: Provenance-Verified Mandatory Swine (12 Items)
Items with `mandatory_swine == True` across all crafting branches. All 12 are curated as `RESTRICTED` with reason `swine`:
1. `pamhc2foodcore:baconandeggsitem`
2. `pamhc2foodcore:baconcheeseburgeritem`
3. `pamhc2foodcore:basicporksandwichitem`
4. `pamhc2foodcore:chocolatebaconitem`
5. `pamhc2foodcore:epicbaconitem`
6. `pamhc2foodcore:grilledcheeseandhamitem`
7. `pamhc2foodcore:grilledporkskeweritem`
8. `pamhc2foodcore:groundporkitem`
9. `pamhc2foodcore:hotdogitem`
10. `pamhc2foodcore:porkjerkyitem`
11. `pamhc2foodcore:porknoodlesoupitem`
12. `pamhc2foodcore:porkpotpieitem`

### Group B: Variable Swine Conflation in Pre-PR #8 Draft (8 Items)
Prior to PR #8 alternative-choice lattice hardening, any recipe branch reaching pork marked the item as high risk. PR #8 separated alternative choice disjunction (`variable_swine == True && mandatory_swine == False`) into `AMBIGUOUS_RECIPE`. The curated pack classifies these as `DOUBTFUL` with reason `variable_provenance`:
1. `pamhc2foodcore:stockitem` (brewed from `#c:stock_ingredients` containing raw pork alongside beef/chicken/fish)
2. `pamhc2foodcore:carrotsoupitem` (requires `#c:stock`)
3. `pamhc2foodcore:potatosoupitem` (requires `#c:stock`)
4. `pamhc2foodcore:pumpkinsoupitem` (requires `#c:stock`)
5. `pamhc2foodcore:noodlesoupitem` (requires `#c:stock`)
6. `pamhc2foodcore:vegetablenoodlesoupitem` (requires `#c:stock`)
7. `pamhc2foodcore:beefnoodlesoupitem` (requires `#c:stock`)
8. `pamhc2foodcore:chickennoodlesoupitem` (requires `#c:stock`)

*(Note: With the 5 additional variable items `muttonnoodlesoupitem`, `rabbitnoodlesoupitem`, `fishnoodlesoupitem`, `meatloafitem`, and `stewitem`, total variable swine items in final engine is 13, all curated as `DOUBTFUL`).*

### Group C: Non-Provenance Direct Heuristic Evidence (1 Item)
An item with 0 recipes in the JAR that was suggested as `HIGH_RISK_RESTRICTED` via direct keyword heuristic + item tag:
1. `pamhc2foodcore:cookedgroundporkitem`: The mod author accidentally omitted `smelting_cookedgroundporkitem.json`, `smoking_cookedgroundporkitem.json`, and `campfire_cookedgroundporkitem.json` from the JAR data. Provenance cannot trace a missing recipe (`mandatory_swine == False`), but name keyword `pork` (weight 1.0) and `#minecraft:meat` tag correctly suggest high risk. Human review confirmed it is cooked ground pork (`RESTRICTED` / `swine`).

### Group D: Stale Historical Result Summary
Sum of Group A (12) + Group B (8) + Group C (1) = **21**. The count 21 was a historical artifact from before alternative choice disjunction was hardened into `AMBIGUOUS_RECIPE`. In the current final engine at depth 16:
- `HIGH_RISK_RESTRICTED`: exactly **13** (Group A [12] + Group C [1])
- `AMBIGUOUS_RECIPE`: exactly **13** (Group B [8] + 5 other variable items)

---

## 2. Hard Invariants Verification

1. **Mandatory Swine Invariant**: For all items in reference JAR 1.0.4, `mandatory_swine == True` strictly implies `curated_status == RESTRICTED` and `reason == swine`. Verified across all 12 items (100% adherence).
2. **Variable Swine Invariant**: `variable_swine == True && mandatory_swine == False` strictly maps to `curated_status == DOUBTFUL` and `reason == variable_provenance`. Verified across all 13 items (100% adherence).
3. **Livestock Meat Invariant**: `mandatory_meat == True && mandatory_swine == False && variable_swine == False` maps to `UNKNOWN` / `unspecified_meat` (29 items), with scaled fish handled separately under `HALAL` / `fish` (2 items: `groundfishitem`, `cookedgroundfishitem`).
4. **No False-Positive Restriction**: Every curated `RESTRICTED / swine` item corresponds to either `mandatory_swine == True` (12 items) or direct high-risk evidence with confirmed pork identity (1 item).

---

## 3. Complete 180-Item Reconciliation Table (Depth 16)

| Item ID | Audit Suggestion | Mand Swine | Var Swine | Mand Meat | Var Meat | Mand Fish | Var Fish | Mand Dairy/Egg | Var Dairy/Egg | Pure Plant | Incomplete | Curated Status | Curated Reason | Override? | Override Rationale |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- | :---: | :--- |
| `appledonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `applejellyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `applejellytoastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `applejuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `applemuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `applepieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `applepopsicleitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `applesauceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `applesmoothieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `appleyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `baconandeggsitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | T | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `baconcheeseburgeritem` | `HIGH_RISK_RESTRICTED` | T | F | T | F | F | F | T | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `bakedvegetablemedlyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `basiccheeseburgeritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `basicchickensandwichitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `basicfishsandwichitem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | T | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `basichamburgeritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `basicmuttonsandwichitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `basicporksandwichitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | T | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `basicrabbitsandwichitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `basicveggieburgeritem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `beefjerkyitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `beefnoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | T | F | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `beefpotpieitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `boiledeggitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `butteredbakedpotatoitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelappleitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `carameldonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelicecreamitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelmuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelpieitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `caramelyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `carrotbreaditem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `carrotcakeitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `carrotdonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `carrotjuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `carrotmuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `carrotpieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `carrotsoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `cheesecakeitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `cheeseitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chickendinneritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | T | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `chickenjerkyitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `chickennoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | T | F | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `chickennuggetitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `chickenpotpieitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `chocolatebaconitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `chocolatebaritem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `chocolatecakeitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolatecaramelfudgeitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolatedonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolateicecreamitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolatemilkitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolatemuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolatepieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `chocolaterollitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chocolateyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chorusdonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chorusjellyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `chorusjellytoastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `chorusjuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `chorusmuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `choruspieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `choruspopsicleitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `chorussmoothieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `chorusyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `cookedgroundbeefitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `cookedgroundchickenitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `cookedgroundfishitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | Yes | Tagged #minecraft:meat by mod author; recipe provenance traces to scaled fish (#c:fishes). |
| `cookedgroundmuttonitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `cookedgroundporkitem` | `HIGH_RISK_RESTRICTED` | F | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | Yes | Mod author omitted smelting recipe for cooked pork in JAR; human review confirmed swine derivative. |
| `cookedgroundrabbititem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `cookiesandmilkitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `cottoncandyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `crackeritem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `crackersandcheeseitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `epicbaconitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `fishandchipsitem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `fishjerkyitem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `fishnoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | T | F | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `fishpotpieitem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `fishsticksitem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `friedchickenitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `friedeggitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `friesitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `fruitpunchitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `fruitsaladitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `fudgesicleitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `glazedcarrotsitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `glowberrydonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `glowberryjellyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `glowberryjellytoastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `glowberryjuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `glowberrymuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `glowberrypieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `glowberrypopsicleitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `glowberrysmoothieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `glowberryyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `grilledbeefskeweritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `grilledcheeseandhamitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | T | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `grilledcheeseitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `grilledchickenskeweritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `grilledfishskeweritem` | `FISH_REVIEW_BASELINE` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | No | Direct adherence to evidence category. |
| `grilledmuttonskeweritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `grilledporkskeweritem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `grilledrabbitskeweritem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `grilledveggieskeweritem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `groundbeefitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `groundchickenitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `groundfishitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | F | F | T | F | F | F | F | F | **HALAL** | `fish` | Yes | Tagged #minecraft:meat by mod author; recipe provenance traces to scaled fish (#c:fishes). |
| `groundmuttonitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `groundporkitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `groundrabbititem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `gummycreepersitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `honeyglazeddonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `honeymuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `honeypieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `audited_permitted_recipe` | Yes | Neutral bake with honey; human review verified honey nectar + flour/water dough. |
| `hotchocolateitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `hotdogitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | T | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `icecreamitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `jellybeansitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `macncheeseitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `marshmellowsitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `mashedpotatoesitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `meatloafitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `melondonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `melonjellyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `melonjellytoastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `melonjuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `melonmuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `melonpieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `melonpopsicleitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `melonsmoothieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `melonyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `muttonjerkyitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `muttonnoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | T | F | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `muttonpotpieitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `noodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `p8juiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `pickledbeetsitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `plaindonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `porkjerkyitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `porknoodlesoupitem` | `HIGH_RISK_RESTRICTED` | T | F | F | T | F | T | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `porkpotpieitem` | `HIGH_RISK_RESTRICTED` | T | F | F | F | F | F | F | F | F | F | **RESTRICTED** | `swine` | No | Direct adherence to evidence category. |
| `potatochipsitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `potatosoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `potroastitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `powdereddonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `pumpkinbreaditem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `pumpkincheesecakeitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `pumpkindonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `pumpkinmuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `pumpkinsoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `pumpkinyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `rabbitjerkyitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `rabbitnoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | T | F | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `rabbitpotpieitem` | `MEAT_PROVENANCE_REQUIRED` | F | F | T | F | F | F | F | F | F | F | **UNKNOWN** | `unspecified_meat` | No | Direct adherence to evidence category. |
| `roastedsunflowerseedsitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `scrambledeggitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `smoresitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `softpretzelitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `sprinklesdonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `stewitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `stockitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `sunflowerseedsitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `sweetberrydonutitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `sweetberryjellyitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `sweetberryjellytoastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `sweetberryjuiceitem` | `LIKELY_PLANT_BASED` | F | F | F | F | F | F | F | F | T | F | **HALAL** | `plant_based` | No | Direct adherence to evidence category. |
| `sweetberrymuffinitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `sweetberrypieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `sweetberrypopsicleitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `sweetberrysmoothieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `sweetberryyogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `toastitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
| `trailmixitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `vegetablenoodlesoupitem` | `AMBIGUOUS_RECIPE` | F | T | F | T | F | T | F | F | F | F | **DOUBTFUL** | `variable_provenance` | No | Direct adherence to evidence category. |
| `veggiepotpieitem` | `NO_SIGNAL` | F | F | F | F | F | F | F | F | F | F | **HALAL** | `plant_based` | Yes | Neutral item; human recipe inspection confirmed permissible plant/mineral ingredients. |
| `yogurtitem` | `LIKELY_LOW_RISK_RECIPE` | F | F | F | F | F | F | T | F | F | F | **HALAL** | `audited_permitted_recipe` | No | Direct adherence to evidence category. |
