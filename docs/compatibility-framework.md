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

Standard system identities:
- `muslimqol:user_override`: Local player configuration overrides
- `muslimqol:datapack`: Datapack JSON and custom rule definitions
- `muslimqol:item_tag`: Item tags (`#muslimqol:food/*`)
- `muslimqol:builtin`: Built-in vanilla dietary database

Third-party mods and datapacks can define distinct provider identities (e.g. `farmersdelight:datapack`, `examplemod:compat_provider`).

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

## 3. Conflict Resolution & Determinism

When two or more providers at the same priority tier classify the same item:

1. **Same Status**: If both providers propose the same `FoodStatus` (e.g. both classify as `HALAL`), resolution succeeds safely without conflict.
2. **Conflicting Status**: If providers propose conflicting statuses (e.g. `HALAL` vs `RESTRICTED` at `DATAPACK` priority), the engine:
   - Selects a **deterministic winner** using provider ID lexicographical sorting (`providerA.id().compareTo(providerB.id())`).
   - Flags `conflicted = true` in the diagnostic resolution model.
   - Logs diagnostic debug information for server administrators and modders.
3. **Determinism Guarantee**:
   $$\text{same input item} + \text{same provider set} = \text{same resolution every time}$$
   Resolution order does not depend on hash-map traversal or thread scheduling.

---

## 4. Diagnostic Resolution Model

The framework separates fast gameplay lookup from rich diagnostic inspection:

```java
// Fast path for gameplay events, tooltips, and rendering:
FoodClassification classification = FoodClassifier.classify(itemStack);

// Diagnostic path for commands, debuggers, and future compatibility UI:
ClassificationResolution resolution = FoodClassifier.resolve(itemStack);

FoodClassification winner = resolution.selected();
List<FoodClassificationCandidate> candidates = resolution.candidates();
boolean hasConflict = resolution.conflicted();
```

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

### Missing Target Mod Handling

If `target_mod` is specified but that mod is not installed in the Minecraft instance:
- MuslimQoL **safely skips** loading the food classifications from that namespace.
- A single informational log entry is recorded at reload time:
  ```text
  Skipped compatibility pack 'Farmer's Delight Compatibility' (farmersdelight) because target mod 'farmersdelight' is not loaded
  ```
- No runtime exceptions, no hard dependencies, and no repetitive tick logging.
- Existing v0.1 datapacks without `compatibility.json` continue to load unconditionally.

---

## 6. Commands & Diagnostics

### `/muslimqol classify <item>`

Provides a detailed provenance inspection:

```text
Item: minecraft:porkchop | Resolved: RESTRICTED
Winner:
  Source: muslimqol:builtin
  Type: BUILTIN
  Priority: BUILTIN
  Reason: swine
Candidates / Overridden:
  RESTRICTED <- muslimqol:builtin [WINNER]
Conflict: no
```

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

## 7. Thread-Safety & Reload Architecture

All providers and compatibility metadata are bundled into an immutable `CompatibilitySnapshot`.

During a resource reload (`/reload` or server start):
1. A fresh snapshot is built and validated in memory.
2. The active reference is atomically updated (`AtomicReference.set(...)`).
3. Reader threads (rendering, tooltips, server event handlers) never observe partial or inconsistent registration state.
