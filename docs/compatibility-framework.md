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

## 1. Provider Identity (`ClassificationProviderId`)

Every classification contributor is identified by a namespace-safe `ClassificationProviderId` wrapping a Minecraft `ResourceLocation`.

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

When multiple providers contribute classifications:

1. **Datapack Multi-Candidate Preservation**:
   If multiple datapacks classify the same item, all candidates are preserved across namespaces instead of being overwritten via "last-write-wins".
2. **Same Status**:
   If competing providers at the highest matching priority tier propose identical `FoodStatus`, resolution succeeds cleanly with `conflicted = false`.
3. **Conflicting Status**:
   If competing providers at the highest matching priority propose differing statuses (e.g. `HALAL` vs `RESTRICTED` at `DATAPACK` priority):
   - A **deterministic winner** is selected using provider ID lexicographical order (`providerA.id().compareTo(providerB.id())`).
   - `conflicted = true` is flagged in the `ClassificationResolution`.
   - All competing candidates are retained in `resolution.candidates()` for inspection.
4. **Determinism Guarantee**:
   $$\text{same input item} + \text{same provider set} = \text{same resolution every time}$$
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
  "name": "Farmer's Delight Compatibility",
  "target_mod": "farmersdelight"
}
```

### Format Validation
The `format` field specifies the metadata schema version (currently `1`). Descriptors with unknown or unsupported format numbers (e.g. `999` or non-positive values) are safely rejected and logged with a warning, preventing malformed metadata from causing runtime issues.

### Missing Target Mod Handling
If `target_mod` is specified but that mod is not installed in the Minecraft instance:
- MuslimQoL **safely skips** loading the food classifications from that namespace.
- An informational log entry is recorded at reload time:
  ```text
  Skipped compatibility pack 'Farmer's Delight Compatibility' (farmersdelight) because target mod 'farmersdelight' is not loaded
  ```
- No runtime exceptions, no hard dependencies, and no repetitive tick logging.
- Existing v0.1 datapacks without `compatibility.json` continue to load unconditionally.

---

## 6. Commands & Diagnostics

### `/muslimqol classify <item>`

Provides a detailed provenance inspection.

**Classified Item Output:**
```text
Item: minecraft:porkchop | Resolved: RESTRICTED
Winner:
  Source: muslimqol:builtin
  Type: BUILTIN
  Priority: BUILTIN
  Reason: swine
Candidates:
  RESTRICTED <- muslimqol:builtin
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

## 7. True Atomic Reload Semantics & Concurrency

MuslimQoL guarantees that readers never observe empty or partially loaded states during resource reloads (`/reload` or server startup):

1. **Atomic Snapshot Swap**: All registered providers and compatibility metadata are bundled into an immutable `CompatibilitySnapshot`, stored in an `AtomicReference`.
2. **Atomic Registry Map Swap**: Datapack classifications and user overrides in `FoodClassificationRegistry` are maintained in `AtomicReference<Map<ResourceLocation, ...>>`.
3. **No Intermediate Cleared States**: During reload, all JSON files and datapacks are fully parsed and assembled into new immutable maps in memory before being swapped in a single atomic step.
4. **Thread Safety**: Concurrent readers (server tick loop, item consumption events, client tooltips) always read either the complete previous state or the complete new state, never an empty or half-populated map.
