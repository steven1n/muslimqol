# MuslimQoL Architecture & System Design

MuslimQoL is designed with a strict layer-separated architecture to ensure stability, server authoritativeness, data-driven extensibility, and loader independence.

---

## Architectural Principles

### 1. Server Authoritativeness
Client UI is strictly informational. All consumption rules (`BLOCK`, `WARN`, `ALLOW`) and entity mechanics (pig spawn suppression, drop filtering) are enforced on the logical server. Clients cannot bypass food restrictions by modifying client configurations.

### 2. Physical Side Isolation
Client-only classes reside exclusively in `io.github.muslimqol.client.*` and are initialized conditionally via `Dist.CLIENT` checks during bootstrap. Dedicated servers never load, reference, or initialize OpenGL, textures, HUD decorators, or GUI rendering classes.

### 3. Registry Safety
MuslimQoL adheres to non-destructive entity and item handling:
- `minecraft:pig`, `minecraft:porkchop`, and `minecraft:cooked_porkchop` remain permanently registered.
- Spawn cancellation uses event interception (`FinalizeSpawnEvent`).
- Drop suppression uses drop interception (`LivingDropsEvent`).
- Third-party recipes, advancements, and mod references to vanilla identifiers remain completely uncorrupted.

### 4. Deterministic Priority Cascade & Conflict Resolution
Food classification queries follow a deterministic five-tier cascade:
```
USER_OVERRIDE
     ↓
  DATAPACK
     ↓
  ITEM_TAG
     ↓
  BUILTIN
     ↓
  UNKNOWN (Safe Fallback)
```

- **Conflict Preservation**: If multiple datapacks or rules register conflicting statuses for an item at the `DATAPACK` tier, all candidates are preserved, `conflicted = true` is reported, and a deterministic winner is selected via strict tie-breaking: `priority DESC`, `providerId ASC`, `ruleId ASC`, `status name ASC`, `reason ASC`.
- **Fast Path vs Full Resolution**:
  - `FoodClassifier.classify(item)` evaluates tiers greedily and short-circuits on the winning tier without candidate collection allocations, maintaining identical tie-break behavior to `resolve`.
  - `FoodClassifier.resolve(item)` gathers candidates across all tiers for diagnostics and debug tools.
- **Fallback Semantics**: Unclassified items yield an empty candidate list (`candidates().isEmpty()`) and a synthetic `UNKNOWN` fallback without falsely attributing the status to built-in rules.

### 5. Transactional State Swapping & Single-Generation Guarantee
All runtime classification mappings, user overrides, active compatibility packs, and skipped compatibility packs are bundled into an immutable `ClassificationRuntimeState` record held in `FoodClassificationRegistry` via `AtomicReference`. Reload operations (`/reload`, server bootstrap) construct complete runtime states before swapping references atomically. Active queries capture a `CompatibilitySnapshot` once at initiation, guaranteeing that reader threads never observe partially populated, cleared, or hybrid intermediate states.

### 6. Provider Canonicalization & Reserved IDs
The framework prevents third-party providers from registering reserved system IDs (`muslimqol:user_override`, `muslimqol:datapack`, `muslimqol:item_tag`, `muslimqol:builtin`) or elevating their priority beyond their declared registration level.

---

## Package Overview

```
io.github.muslimqol/
├── api/             # Immutable records, core contracts, and public enums
│   ├── FoodStatus.java
│   ├── FoodClassification.java
│   ├── ClassificationSource.java
│   ├── ClassificationPriority.java
│   ├── ClassificationProviderId.java
│   ├── ClassificationRuleId.java (Experimental v0.2)
│   ├── FoodClassificationCandidate.java
│   ├── ClassificationResolution.java
│   ├── FoodClassificationProvider.java (Experimental v0.2)
│   ├── ConsumptionPolicy.java
│   └── PigPolicy.java
│
├── compat/          # Multi-provider resolution engine and compatibility management (Experimental v0.2)
│   ├── FoodCompatibilityManager.java
│   ├── CompatibilitySnapshot.java
│   ├── CompatibilityMetadata.java
│   ├── MetadataParseResult.java
│   └── ClassificationRuntimeState.java
│
├── food/            # Classification logic and builtin datasets
│   ├── FoodClassifier.java
│   ├── FoodClassificationRegistry.java
│   ├── BuiltinFoodData.java
│   └── FoodTagResolver.java
│
├── config/          # Common and Client configuration specifications
│   ├── CommonConfig.java
│   └── ClientConfig.java
│
├── data/            # Datapack loaders and reload listeners
│   ├── FoodClassificationJsonLoader.java
│   └── FoodClassificationReloadListener.java
│
├── event/           # Server-side event subscribers
│   ├── FoodConsumptionHandler.java
│   ├── PigSpawnHandler.java
│   └── PigDropHandler.java
│
├── client/          # Client-only rendering, tooltips, and overlay decorators
│   ├── ClientInit.java
│   ├── FoodTooltipHandler.java
│   └── FoodOverlayRenderer.java
│
├── command/         # Server/Client command registry
│   └── MuslimQolCommands.java
│
└── util/            # Safe ResourceLocation and parsing utilities
    └── ResourceLocationUtil.java
```

---

## Future Multi-Loader Roadmap

Core packages (`api`, `food`, and parsing algorithms in `data`) maintain zero reliance on NeoForge-specific internals outside standard Minecraft abstractions, ensuring clean future porting to Fabric.
