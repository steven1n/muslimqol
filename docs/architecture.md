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

### 4. Deterministic Priority Cascade
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

No heuristic guessing or unverified assumptions occur. If an item cannot be resolved through one of the four explicit sources, it falls back safely to `FoodStatus.UNKNOWN`.

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
│   ├── FoodClassificationCandidate.java
│   ├── ClassificationResolution.java
│   ├── FoodClassificationProvider.java (Experimental v0.2)
│   ├── ConsumptionPolicy.java
│   └── PigPolicy.java
│
├── compat/          # Multi-provider resolution engine and compatibility management (Experimental v0.2)
│   ├── FoodCompatibilityManager.java
│   ├── CompatibilitySnapshot.java
│   └── CompatibilityMetadata.java
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
