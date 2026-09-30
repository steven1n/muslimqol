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

### 7. Dedicated Server to Client Classification Synchronization
When connected to a dedicated server, datapack reload listeners (`FoodClassificationReloadListener`) and server configuration (`CommonConfig`) execute only on the server JVM. To ensure client tooltips (`FoodClassificationTooltipFormatter`) and inventory overlays (`FoodOverlayRenderer`) reflect the server's active rules without compromising server authoritativeness or physical side isolation:
- **Scoped Snapshot (`ClientSyncedClassificationState`)**: The server sends `ClassificationSyncPayload` (`muslimqol:classification_sync`, protocol `"1"`, registered with `PayloadRegistrar.optional()`) containing only `datapackEntries`, `userOverrides`, and the four `ConsumptionPolicy` values (`halal`, `restricted`, `doubtful`, `unknown`). Server-only compatibility diagnostics (`activePacks`, `skippedPacks`, `packStates`) are excluded, and the client applies the snapshot via `FoodClassificationRegistry.applyClientSyncedState(...)` without invoking `FoodCompatibilityManager.applyRuntimeState(...)`.
- **Sync Triggers**: Sent on `OnDatapackSyncEvent` (player login and `/reload`), `/muslimqol reload`, and `ModConfigEvent.Reloading` (`ServerLifecycleHooks.getCurrentServer()`), gated by `player.connection.hasChannel(ClassificationSyncPayload.TYPE)` so vanilla clients are unaffected.
- **Singleplayer & Disconnect Safety**: `ClientClassificationSyncHandler` ignores sync payloads on integrated singleplayer connections (`isMemoryConnection || mc.hasSingleplayerServer()`) because the client and integrated server already share the same JVM state, and clears `CLIENT_SYNCED_STATE` on `ClientPlayerNetworkEvent.LoggingOut`.
- **Payload Size Bounds & String-Table Deduplication**:
  - Vanilla 1.21.1 enforces `ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE = 1,048,576` bytes (1 MiB); MuslimQoL enforces `MAX_PAYLOAD_BYTES = 960 KiB` (`983,040` bytes).
  - Repeated `reason`, `providerId`, and `ruleId` strings are deduplicated into an indexed string table (`MAX_STRING_TABLE_ENTRIES = 2,048`, `MAX_STRING_LENGTH = 256`).
  - **Repository Dataset Reference Counts**: `BuiltinFoodData` defines 37 vanilla items (`src/main/java/io/github/muslimqol/food/BuiltinFoodData.java`); `muslimqol_farmersdelight` defines 89 items across 10 JSON files (`src/main/resources/data/muslimqol_farmersdelight/muslimqol/food_classifications/*.json`); `muslimqol_pamhc2foodcore` defines 180 items across 6 JSON files (`src/main/resources/data/muslimqol_pamhc2foodcore/muslimqol/food_classifications/*.json`).
  - **Size Estimation at `MAX_DATAPACK_ITEMS = 8,192`** (`MAX_CANDIDATES_PER_ITEM = 8`, `MAX_TOTAL_CANDIDATES = 16,384`, `MAX_USER_OVERRIDES = 1,024`, `MAX_STRING_TABLE_ENTRIES = 2,048`, `MAX_STRING_LENGTH = 256`, `MAX_PAYLOAD_BYTES = 960 KiB` / `983,040` bytes, `VANILLA_MAX_CUSTOM_PAYLOAD_BYTES = 1,048,576` bytes):
    - *Typical upper-bound lengths (`64 B` + `2 B` VarInt = `66 B` per string/ID, `9 B` per indexed candidate)*:
      String table (`2,048 × 66 B ≈ 132.0 KiB`) + Datapack keys (`8,192 × 66 B ≈ 528.0 KiB`) + Candidates (`16,384 × 9 B ≈ 144.0 KiB`) + UserOverrides (`1,024 × 75 B ≈ 75.0 KiB`) + headers (`< 1 KiB`) = **`~880.0 KiB` (`< 960 KiB`)**.
    - *Theoretical maximum string lengths (`MAX_STRING_LENGTH = 256` chars, i.e., `258 B` ASCII or `770 B` 3-byte UTF-8)*:
      String table (`2,048 × 258 B = 528,384 B ≈ 516.0 KiB` ASCII; up to `2,048 × 770 B = 1,576,960 B ≈ 1,540.0 KiB` 3-byte UTF-8) + Datapack `ResourceLocation` keys (`8,192 × 258 B = 2,113,536 B ≈ 2,064.0 KiB`) + Candidates (`16,384 × 9 B = 147,456 B ≈ 144.0 KiB`) + UserOverrides (`1,024 × 267 B = 273,408 B ≈ 267.0 KiB`) = **`3,062,804 B` (`~2,991 KiB ≈ 2.92 MiB` ASCII) to `4,111,380 B` (`~4,015 KiB ≈ 3.92 MiB` UTF-8)**, which exceeds `960 KiB`.
- **Over-Limit Truncation, Final Byte-Size Guard & Future Chunked Streaming**:
  - **Count Guard & Final Byte-Size Guard（最终体积守卫）**: Encoding first applies count limits (`MAX_DATAPACK_ITEMS = 8,192`, `MAX_CANDIDATES_PER_ITEM = 8`, `MAX_TOTAL_CANDIDATES = 16,384`, `MAX_USER_OVERRIDES = 1,024`, `MAX_STRING_TABLE_ENTRIES = 2,048`) and string length clamping (`MAX_STRING_LENGTH = 256`), and then enforces a **final byte-size guard** before writing to the buffer: if total serialized bytes still exceed `MAX_PAYLOAD_BYTES` (`960 KiB` = `983,040` bytes), excess entries are deterministically dropped from the lexicographical tail of `datapackEntries` (and `userOverrides` if needed) with string-table compaction until the payload fits within `960 KiB`. Truncation warnings record the last truncated item namespace set (`truncatedNamespaces`) and re-emit whenever that namespace set changes. For unexpected runtime failures during encoding, `ClassificationSyncPayload.encode` **已捕获运行时异常并降级为空快照** (catches `RuntimeException`, logs the error with stack trace via `LOGGER.error` without warn-once suppression, and falls back to an empty classification snapshot).
  - **Truncation Semantics（截断后服务端仍拦截，客户端可能显示 Unknown）**: Server-side consumption enforcement remains 100% active for all items, while truncated items may display as `UNKNOWN` on the client.
  - **Future Roadmap（后续改为分包）**: Subsequent iterations will replace single-packet truncation with multi-packet chunked streaming.

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
│   ├── ClientSyncedClassificationState.java
│   ├── BuiltinFoodData.java
│   └── FoodTagResolver.java
│
├── network/         # Server-to-client custom payload and sync dispatch
│   ├── ClassificationSyncPayload.java
│   └── MuslimQolNetwork.java
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
├── client/          # Client-only rendering, tooltips, overlay decorators, and sync receiver
│   ├── ClientInit.java
│   ├── ClientClassificationSyncHandler.java
│   ├── FoodClassificationTooltipFormatter.java
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
