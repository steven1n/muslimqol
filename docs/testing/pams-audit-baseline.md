# Pam's HarvestCraft 2 - Food Core: Untouched Baseline Audit Run

## Target Artifact Details

- **JAR Filename**: `pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar`
- **Mod Version**: `1.0.4`
- **Minecraft Version**: `1.21.1`
- **Mod Loader**: NeoForge `[21.1.0,)`
- **JAR Size**: `1,699,044` bytes
- **SHA-256**: `acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9`
- **Mod ID**: `pamhc2foodcore` (verified from `META-INF/neoforge.mods.toml`)

---

## Untouched Generic Engine First-Run Output

Command:
```bash
python3 -m tools.compatibility.audit.cli \
  --jar /Users/akiyama/.gemini/antigravity/brain/50f5d25e-a862-4407-af5a-5359a03241ce/scratch/pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar \
  --mod-id pamhc2foodcore \
  --output build/audit/pams-food-core
```

Output:
```text
=== MuslimQoL Compatibility Audit Engine ===
Target Mod ID: pamhc2foodcore
Mod JAR Path:  /Users/akiyama/.gemini/antigravity/brain/50f5d25e-a862-4407-af5a-5359a03241ce/scratch/pamhc2foodcore-NEOFORGE-1.21.1-1.0.4.jar
JAR SHA-256:         acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9
Discovered items:    209
Edible candidates:   12
Parsed recipes:      218
Parsed item tags:    197
JAR Diagnostics:     0

--- Heuristic Suggestions Breakdown ---
  HIGH_RISK_RESTRICTED      : 0
  MEAT_PROVENANCE_REQUIRED  : 4
  LIKELY_PLANT_BASED        : 0
  LIKELY_LOW_RISK_RECIPE    : 0
  FISH_REVIEW_BASELINE      : 0
  SEAFOOD_REVIEW            : 0
  AMBIGUOUS_RECIPE          : 0
  GENERAL_REVIEW            : 0
  NO_SIGNAL                 : 8

Items with detected conflicts: 0

Generated Audit Artifacts:
  Summary:  build/audit/pams-food-core/summary.json
  Evidence: build/audit/pams-food-core/evidence.json
  Review:   build/audit/pams-food-core/review.md
```

---

## Discovered Metrics & Baseline Capture

| Metric | Baseline Count |
| :--- | :---: |
| **Total Registry Items Discovered** | 209 |
| **Edible Candidates Audited** | 12 |
| **Parsed Recipes** | 218 |
| **Parsed Item Tags** | 197 |
| **Parser Diagnostics** | 0 |
| **`HIGH_RISK_RESTRICTED`** | 0 |
| **`MEAT_PROVENANCE_REQUIRED`** | 4 |
| **`LIKELY_PLANT_BASED`** | 0 |
| **`LIKELY_LOW_RISK_RECIPE`** | 0 |
| **`FISH_REVIEW_BASELINE`** | 0 |
| **`SEAFOOD_REVIEW`** | 0 |
| **`AMBIGUOUS_RECIPE`** | 0 |
| **`GENERAL_REVIEW`** | 0 |
| **`NO_SIGNAL`** | 8 |
| **Items with Conflicts** | 0 |

---

## Initial Observations on Generalization Gaps

1. **Edible Candidate Under-Discovery**:
   - Total items discovered by bytecode: **209**.
   - Edible candidates identified: **12**.
   - The mod claims to add ~180 foods, but only 12 items were identified as edible candidates. This indicates Pam's HarvestCraft 2 Food Core does not use the standard `c:foods` tag hierarchy for all items, or uses an alternative tag/component scheme.
2. **Name Tokenization Ineffective on Concatenated Identifiers**:
   - Pam's identifiers use lowercase un-delimited concatenations with an `item` suffix (e.g. `hotdogitem`, `chickendinneritem`, `fudgesicleitem`, `cookedgroundbeefitem`).
   - The current tokenizer relies on snake_case (`_`), hyphens (`-`), and camelCase. Without delimiters, tokens like `hotdogitem` fail to split into `hotdog` and are unrecognized, leading to `NO_SIGNAL`.
3. **Recipe and Tag Parsing Successfully Generalizes**:
   - 218 recipes and 197 item tags parsed with 0 crashes and 0 parser diagnostics.
