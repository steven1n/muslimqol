# MuslimQoL Compatibility Framework

> [!WARNING]
> **Experimental API Status (v0.2.x)**  
> The Food Compatibility API is currently **experimental** during the MuslimQoL 0.2 development cycle. Classes and method signatures in `io.github.muslimqol.api` and `io.github.muslimqol.compat` are subject to refinement based on community feedback prior to 1.0.

---

## Overview

The MuslimQoL Compatibility Framework is an extensible architecture designed to allow third-party mods, datapacks, and community integrations to register dietary food classifications cleanly and deterministically.

The framework answers five fundamental questions for any item:
1. **What classification does this item currently have?** (`HALAL`, `RESTRICTED`, `DOUBTFUL`, or `UNKNOWN`)
2. **Why does it have this classification?** (e.g. `plant_based`, `swine`, `datapack`)
3. **Where did that classification originate?** (e.g. `muslimqol:builtin`, `farmersdelight:datapack`)
4. **Which rule overrode another rule?** (precedence cascade and diagnostic candidates list)
5. **Are there competing rules at the same priority?** (conflict detection with deterministic tie-breaking)

---

## 1. Provider Identity (`ClassificationProviderId`) & Rule Provenance (`ClassificationRuleId`)

Every classification contributor is identified by two distinct identifiers:
- **`ClassificationProviderId`**: A namespace-safe identifier for the contributor/system (e.g. `muslimqol:builtin`, `farmersdelight:datapack`).
- **`ClassificationRuleId`**: A specific source or file definition identifier (e.g. `farmersdelight:food_classifications/crops`, `muslimqol:food_classifications/builtin`).

### Reserved System Identities
The following provider IDs are reserved for MuslimQoL's internal pipeline:
- `muslimqol:user_override`: Local player configuration overrides
- `muslimqol:datapack`: Datapack JSON and custom rule definitions
- `muslimqol:item_tag`: Item tags (`#muslimqol:food/*`)
- `muslimqol:builtin`: Built-in vanilla dietary database

> [!IMPORTANT]
> External providers cannot register using any of the reserved system IDs (`IllegalArgumentException` is thrown). Furthermore, reserved system providers cannot be unregistered.

### Canonicalization & Normalization
Third-party providers cannot spoof identity or self-promote priority. The resolution engine canonicalizes all candidate classifications returned by a provider:
- `candidate.providerId()` is strictly bound to the provider's registered `provider.id()`.
- `candidate.priority()` is clamped to the provider's registered `provider.priority()`.

---

## 2. Priority Hierarchy (`ClassificationPriority`)

Classifications are evaluated according to a strict, immutable priority hierarchy:

| Tier | Priority Enum | Numeric Level | Description |
| :--- | :--- | :---: | :--- |
| 1 | `USER_OVERRIDE` | 400 | Local user overrides in `config/muslimqol-common.toml` |
| 2 | `DATAPACK` | 300 | Datapack JSON classifications and third-party compatibility packs |
| 3 | `ITEM_TAG` | 200 | Datapack item tags (`#muslimqol:food/halal`, `restricted`, `doubtful`) |
| 4 | `BUILTIN` | 100 | Built-in vanilla Minecraft food classification mappings |
| 5 | `UNKNOWN` | 0 | Default fallback for unclassified foods |

Higher priority levels always override lower priority levels.

---

## 3. Conflict Resolution & Datapack Preservation

When multiple rules or providers contribute classifications:

1. **Datapack Multi-Candidate Preservation**:
   If multiple datapacks or multiple rule files within a datapack classify the same item, all candidates are preserved across namespaces and rule files instead of being overwritten via "last-write-wins".
2. **Same Status**:
   If competing providers or rules at the highest matching priority tier propose identical `FoodStatus`, resolution succeeds cleanly with `conflicted = false`.
3. **Conflicting Status**:
   If competing candidates at the highest matching priority propose differing statuses (e.g. `HALAL` vs `RESTRICTED` at `DATAPACK` priority):
   - A **deterministic winner** is selected using strict tie-breaking criteria:
     1. Priority level descending (`priority DESC`)
     2. Provider ID lexicographical ascending (`providerId ASC`)
     3. Rule ID lexicographical ascending (`ruleId ASC`)
     4. Final stable tie-breaker: `status name ASC`, followed by `reason ASC`.
   - `conflicted = true` is flagged in the `ClassificationResolution`.
   - All competing candidates are retained in `resolution.candidates()` for inspection.
4. **Determinism Guarantee**:
   $$\text{same input item} + \text{same candidate set} = \text{same resolution every time}$$
   Resolution order does not depend on hash-map traversal or thread scheduling.

---

## 4. Fast Path vs Diagnostic Resolution

The framework provides two distinct resolution pathways:

```java
// Fast path for gameplay events, item consumption, and tooltip rendering:
FoodClassification classification = FoodClassifier.classify(itemStack);

// Diagnostic path for commands, debug tools, and admin inspections:
ClassificationResolution resolution = FoodClassifier.resolve(itemStack);
```

### Performance Fast Path (`classify`)
- Evaluates priority tiers from highest (`USER_OVERRIDE`) to lowest (`BUILTIN`).
- **Short-circuits immediately** upon finding a match in the active tier.
- Avoids allocating candidate lists and avoids querying lower-priority tiers when a higher tier has matched.
- Uses identical candidate tie-breaking to ensure `FoodClassifier.classify(item)` is strictly equivalent to `FoodClassifier.resolve(item).selected()`.

### Diagnostic Path (`resolve`)
- Queries all registered providers across all priority tiers.
- Preserves all candidates in `resolution.candidates()`.
- Calculates conflict status across candidates at the winning priority tier.

---

## 5. Compatibility Datapack Metadata (`compatibility.json`)

Compatibility packs may optionally include a `compatibility.json` descriptor in their root namespace:

```text
data/<namespace>/muslimqol/compatibility.json
```

### Schema

```json
{
  "format": 1,
  "name": "MuslimQoL Pam's HarvestCraft 2 Food Core Compatibility",
  "target_mod": "pamhc2foodcore",
  "target_version": "1.0.4",
  "reference_jar_sha256": "acd5dd380eafc3f317b67231a2355c204cab254dfdf4ab1f25c084fdd3d317b9"
}
```

- `format` (int, required): Format specification number (currently `1`).
- `name` (string, optional): Human-readable pack title.
- `target_mod` (string, optional): Mod ID required for activation (null for universal packs).
- `target_version` (string, optional): Target mod version audited against (null for unversioned legacy packs).
- `reference_jar_sha256` (string, optional): 64-character hexadecimal SHA-256 hash of the audited reference JAR (informational audit provenance; MuslimQoL does not hash installed mod JARs at runtime).

### Runtime Trust States (`CompatibilityVerificationStatus`)

When evaluating loaded compatibility packs, the engine establishes a deterministic verification state:

1. **`VERIFIED`**:
   - `target_mod` is loaded and its installed version exactly matches `target_version`.
   - Legacy packs without `target_version` load as `VERIFIED (legacy unversioned pack)`.
2. **`UNVERIFIED`**:
   - `target_mod` is loaded, but its installed version differs from `target_version` (or could not be determined).
   - The pack **remains active by default**, ensuring gameplay continues uninterrupted. A warning is logged:
     ```text
     Compatibility pack '...' was audited for <mod> <target_version>, but installed version is <installed_version>. The pack remains active but is UNVERIFIED for this version.
     ```
   - *Note*: `UNVERIFIED` does not imply incompatibility; it signals that the specific installed version has not yet been audited.
3. **`SKIPPED`**:
   - `target_mod` is not loaded, or the metadata is invalid. The pack is completely inactive.

### Parse States (`MetadataParseResult`)
The loader classifies pack metadata into three distinct states:
1. **`Absent`**: No `compatibility.json` descriptor present. The datapack is treated as a standard v0.1 legacy pack and loads unconditionally.
2. **`Valid`**: Contains valid format `1` metadata. Evaluated via `CompatibilityVerificationStatus`.
3. **`Invalid`**: Unsupported format version (e.g. `999` or non-positive value), malformed JSON, invalid string types, or malformed SHA-256 strings.
   - MuslimQoL **skips all food classifications from that namespace** and logs a warning:
     ```text
     Skipping compatibility classifications for namespace 'futurepack' due to invalid metadata: Unsupported format version: 999
     ```
   - Invalid packs **never fall back** to unconditional loading.

---

## 6. Commands & Diagnostics

### `/muslimqol classify <item>`

Provides a detailed provenance inspection including rule identifier:

**Classified Item Output:**
```text
Item: minecraft:porkchop | Resolved: RESTRICTED
Winner:
  Source: muslimqol:builtin
  Rule: muslimqol:food_classifications/builtin
  Type: BUILTIN
  Priority: BUILTIN
  Reason: swine
Candidates:
  RESTRICTED <- muslimqol:builtin [rule: muslimqol:food_classifications/builtin]
Conflict: no
```

**Unclassified (UNKNOWN Fallback) Output:**
```text
Item: examplemod:mystery_berry | Resolved: UNKNOWN
Winner: Fallback: No classification available
Candidates: none
Conflict: no
```
*Note: Unclassified items produce an empty candidate list (`candidates() == empty`). The diagnostic command displays fallback text rather than falsely attributing the UNKNOWN status to `muslimqol:builtin`.*

### `/muslimqol providers` (or `/muslimqol compat`)

Inspects registered classification providers and loaded/skipped compatibility packs:

```text
Registered classification providers:
  muslimqol:user_override (USER_OVERRIDE)
  muslimqol:datapack (DATAPACK)
  muslimqol:item_tag (ITEM_TAG)
  muslimqol:builtin (BUILTIN)
```

---

## 7. True Transactional Reload Semantics & Concurrency

MuslimQoL guarantees that readers never observe empty, partially loaded, or hybrid states during resource reloads (`/reload` or server startup):

1. **Unified Runtime State (`ClassificationRuntimeState`)**:
   Datapack classification mappings, user overrides, active compatibility packs, and skipped compatibility packs are bundled into an immutable `ClassificationRuntimeState` record.
2. **Single Atomic Swap**:
   During reload, all JSON files and datapacks are fully parsed and assembled into the new runtime state before being swapped in a single atomic operation via `FoodClassificationRegistry.applyRuntimeState(...)`.
3. **Single-Generation Query Guarantee**:
   Queries capture active state once at the start of resolution (`CompatibilitySnapshot`). Consecutive reads within a single resolution or snapshot never observe hybrid generations (e.g., part from generation N and part from generation N+1).
4. **No Intermediate Cleared States**:
   No `clear()` or destructive mutations occur on live reader maps.
5. **Thread Safety**:
   Concurrent readers (server tick loop, item consumption events, client tooltips) always read either the complete previous generation or the complete new generation.
