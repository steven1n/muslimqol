# Dedicated Server to Client Classification Sync — Reproduction Report

- **Date**: 2026-09-30
- **Minecraft Version**: `1.21.1`
- **NeoForge Version**: `21.1.176` (baseline) / `21.1.219` (Farmer's Delight `1.21.1-1.3.4` runtime)
- **Java Runtime**: Temurin-21.0.11+10-LTS (macOS)
- **Status**: **REPRODUCED & VERIFIED FIXED**

---

## 1. Summary of Issue

When a client connects to a **Dedicated Server** (`./gradlew runServer`), the client's `FoodClassificationRegistry` has **zero `DATAPACK` classifications** and reads **local client `CommonConfig`** instead of the server's `USER_OVERRIDE` rules and consumption policies (`HALAL_POLICY`, `RESTRICTED_POLICY`, `DOUBTFUL_POLICY`, `UNKNOWN_POLICY`).

As a result:
1. All mod compatibility pack items (such as Farmer's Delight items in `data/muslimqol_farmersdelight/` and Pam's HarvestCraft 2 items in `data/muslimqol_pamhc2foodcore/`) resolve to `UNKNOWN` (`unclassified`) in client tooltips (`FoodClassificationTooltipFormatter`) and inventory overlays (`FoodOverlayRenderer`), even while the dedicated server enforces their true `RESTRICTED` / `DOUBTFUL` / `HALAL` status during consumption and reports them accurately via `/muslimqol classify`.
2. Any server-configured `user_overrides` or custom datapack overrides for vanilla items are invisible to connected clients.
3. Client tooltip policy lines (`Blocked by MuslimQoL dietary policy`, `Warning: Doubtful food`, etc.) reflect the client's local `muslimqol-common.toml` rather than the dedicated server's enforced policies.

---

## 2. Reproduction Steps

### Prerequisites
Ensure `run/eula.txt` has `eula=true` and `run/server.properties` has `online-mode=false` so the development client (`Dev`) can connect locally.

> **Why `-Pneo_version=21.1.219` is used for the Farmer's Delight reproduction**:
> The project's default `neo_version` in `gradle.properties` is `21.1.176`. However, the optional development dependency `farmers-delight-1.21.1-1.3.4` (`-PwithFarmersDelight=true`) declares a minimum loader dependency of `neoforge >= 21.1.219` in its `META-INF/neoforge.mods.toml`. Launching Farmer's Delight `1.3.4` under `21.1.176` aborts at FML mod gathering with:
> `ModLoadingException: Mod farmersdelight requires neoforge 21.1.219 or above (Currently, neoforge is 21.1.176)`.
> Passing `-Pneo_version=21.1.219` satisfies Farmer's Delight's mod-loader constraint while keeping all MuslimQoL code and tests compatible with the project baseline `21.1.176`.

### Step 1: Start Dedicated Server with Farmer's Delight Compatibility Runtime
```bash
./gradlew runServer -PwithFarmersDelight=true -Pneo_version=21.1.219 --console=plain --no-daemon
```

### Step 2: Connect Client to Dedicated Server (`127.0.0.1:25565`)
```bash
./gradlew runClient \
  -PwithFarmersDelight=true \
  -Pneo_version=21.1.219 \
  --args="@$(pwd)/build/moddev/clientRunProgramArgs.txt --quickPlayMultiplayer 127.0.0.1:25565" \
  --console=plain --no-daemon
```

### Step 3: Execute Server Classification Queries & Give Test Items
In the dedicated server console:
```text
muslimqol status
muslimqol classify farmersdelight:bacon
muslimqol classify farmersdelight:cabbage
muslimqol classify farmersdelight:dumplings
give Dev farmersdelight:bacon
give Dev farmersdelight:cabbage
```

---

## 3. Observed Logs (Pre-Fix)

### Dedicated Server Log Excerpt
The dedicated server fires `AddReloadListenerEvent` (`FoodClassificationReloadListener`), verifies the Farmer's Delight compatibility pack, loads 89 datapack classification keys into the server JVM's `FoodClassificationRegistry`, and resolves classifications accurately:

```text
[10:16:13] [main/INFO] [io.gi.mu.da.FoodClassificationReloadListener/]: Applying food classifications from datapacks...
[10:16:13] [main/INFO] [io.gi.mu.da.FoodClassificationReloadListener/]: Compatibility pack 'MuslimQoL Farmer's Delight Compatibility' verified for farmersdelight 1.3.4.
[10:16:13] [main/INFO] [io.gi.mu.da.FoodClassificationReloadListener/]: Skipped compatibility pack 'MuslimQoL Pam's HarvestCraft 2 Food Core Compatibility' (muslimqol_pamhc2foodcore) because target mod 'pamhc2foodcore' is not loaded
[10:16:13] [main/INFO] [io.gi.mu.da.FoodClassificationReloadListener/]: Applied 89 datapack classification keys and 0 user overrides transactionally
[10:16:14] [Server thread/INFO] [minecraft/DedicatedServer]: Starting minecraft server version 1.21.1
...
[10:16:44] [Server thread/INFO] [minecraft/PlayerList]: Dev[/127.0.0.1:58734] logged in with entity id 6 at (8.5, 87.0, -8.5)
[10:16:44] [Server thread/INFO] [minecraft/MinecraftServer]: Dev joined the game
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: MuslimQoL Status: Halal: ALLOW | Restricted: BLOCK | Doubtful: WARN | Unknown: ALLOW | Pig Policy: NORMAL
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Item: farmersdelight:bacon | Resolved: RESTRICTED
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Winner:
  Source: muslimqol_farmersdelight:datapack
  Type: DATAPACK
  Priority: DATAPACK
  Reason: swine
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]:   Rule: muslimqol_farmersdelight:food_classifications/pork
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Item: farmersdelight:cabbage | Resolved: HALAL
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Winner:
  Source: muslimqol_farmersdelight:datapack
  Type: DATAPACK
  Priority: DATAPACK
  Reason: plant_based
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]:   Rule: muslimqol_farmersdelight:food_classifications/plants
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Item: farmersdelight:dumplings | Resolved: DOUBTFUL
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]: Winner:
  Source: muslimqol_farmersdelight:datapack
  Type: DATAPACK
  Priority: DATAPACK
  Reason: unknown_ingredients
[10:16:48] [Server thread/INFO] [minecraft/MinecraftServer]:   Rule: muslimqol_farmersdelight:food_classifications/doubtful
```

### Connected Client Log Excerpt
On the client JVM, `AddReloadListenerEvent` never runs (as datapack reload listeners are server-side resources). No network sync payload is sent upon login (`ConnectScreen: Connecting to 127.0.0.1, 25565`):

```text
[10:16:36] [modloading-worker-0/INFO] [muslimqol/]: Initializing MuslimQoL Mod (v0.1.0)
[10:16:37] [modloading-sync-worker/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Loaded 0 user food override classifications (atomic swap)
[10:16:39] [Render thread/INFO] [minecraft/ReloadableResourceManager]: Reloading ResourceManager: vanilla, mod_resources, mod/farmersdelight, mod/emi, mod/muslimqol, mod/neoforge
[10:16:42] [Render thread/INFO] [minecraft/ConnectScreen]: Connecting to 127.0.0.1, 25565
[10:16:48] [Render thread/INFO] [minecraft/AdvancementTree]: Loaded 20 advancements
```

Notice that `FoodClassificationReloadListener` is **never invoked** in the client log. The client's `FoodClassificationRegistry.getRuntimeState()` remains empty (`0` datapack keys, `0` active compatibility packs).

---

## 4. Server `/muslimqol classify` vs. Client Tooltip Comparison (Pre-Fix)

> **说明（Tooltip 验证方式）**：表中客户端 Tooltip 结果来自 [`FoodClassificationTooltipFormatter.formatTooltip`](file:///Users/akiyama/IdeaProjects/muslimqol/src/main/java/io/github/muslimqol/client/FoodClassificationTooltipFormatter.java) 输出，GUI 悬停未手动验证。

| Item ID | Dedicated Server (`/muslimqol classify`) | Dedicated Server Consumption Enforcement | Client Tooltip (`FoodClassificationTooltipFormatter`) & Overlay (`FoodOverlayRenderer`) | Mismatch? |
| :--- | :--- | :--- | :--- | :---: |
| `farmersdelight:bacon` | **`RESTRICTED`** (`swine`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/pork`) | **Blocked** (`RESTRICTED_POLICY = BLOCK`) | **`? Unknown`** (`Not yet classified`, `NONE`, no policy warning) | **YES (Critical)** |
| `farmersdelight:cabbage` | **`HALAL`** (`plant_based`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/plants`) | **Allowed** (`HALAL_POLICY = ALLOW`) | **`? Unknown`** (`Not yet classified`, `NONE`) | **YES** |
| `farmersdelight:dumplings` | **`DOUBTFUL`** (`unknown_ingredients`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/doubtful`) | **Warned** (`DOUBTFUL_POLICY = WARN`) | **`? Unknown`** (`Not yet classified`, `NONE`, no warning line) | **YES** |
| Vanilla item with server `user_overrides` (e.g. `minecraft:beef=HALAL:zabiha`) | **`HALAL`** (`zabiha`, `USER_OVERRIDE`) | **Allowed** | **`? Unknown`** (`Unspecified meat source`, `BUILTIN` fallback) | **YES** |
| Server custom policy (e.g. `doubtful_policy = "BLOCK"`) | `Doubtful: BLOCK` | **Blocked** | Reads client local `CommonConfig` (`WARN` by default) | **YES** |

---

## 5. Root Cause Analysis

1. **Server-Only Datapack Loading**: `FoodClassificationReloadListener` is registered on `AddReloadListenerEvent`, which attaches to `ReloadableServerResources` on the logical server. In Singleplayer, the integrated server and client share the same JVM and static `FoodClassificationRegistry.RUNTIME_STATE`, masking the bug. On a Dedicated Server, the server and client run in separate JVMs; the client JVM never runs `FoodClassificationReloadListener`.
2. **Missing Network Synchronization**: The codebase has zero `CustomPacketPayload` definitions, zero `RegisterPayloadHandlersEvent` registrations, and zero `OnDatapackSyncEvent` listeners.
3. **Unsynchronized Server Config & Policies**: `CommonConfig.USER_OVERRIDES` and the consumption policies (`HALAL_POLICY`, `RESTRICTED_POLICY`, `DOUBTFUL_POLICY`, `UNKNOWN_POLICY`) are `ModConfig.Type.COMMON` (local file per machine, not automatically synced by NeoForge). When `FoodClassificationTooltipFormatter` calls `FoodClassifier.getPolicy(status)`, it reads the client's local `CommonConfig` instead of the server's active policies.

---

## 6. Post-Fix Verification (`ClassificationSyncPayload` S2C Snapshot Sync)

> **说明（Tooltip 验证方式）**：以下客户端 `Tooltip=[...]` 与分类对比均来自 [`FoodClassificationTooltipFormatter.formatTooltip`](file:///Users/akiyama/IdeaProjects/muslimqol/src/main/java/io/github/muslimqol/client/FoodClassificationTooltipFormatter.java) 输出，GUI 悬停未手动验证。验证完成后已从 `ClientClassificationSyncHandler` 移除临时诊断日志逻辑，默认路径不再调用。

### Dedicated Server + Client Log Excerpt (with Farmer's Delight `1.3.4`, `89` entries from `data/muslimqol_farmersdelight/muslimqol/food_classifications/*.json`)
Upon player login (`OnDatapackSyncEvent`), `/reload`, `/muslimqol reload`, or `ModConfigEvent.Reloading`, the dedicated server sends `ClassificationSyncPayload` (`muslimqol:classification_sync`) to connected modded clients:

```text
# Server Log:
[10:58:26] [Server thread/DEBUG] [io.gi.mu.ne.MuslimQolNetwork/]: Sent ClassificationSyncPayload (89 datapack keys, 0 user overrides) to 1 player(s)
[10:58:30] [Server thread/INFO] [minecraft/MinecraftServer]: Reloading!
[10:58:30] [Server thread/INFO] [io.gi.mu.da.FoodClassificationReloadListener/]: Applied 89 datapack classification keys and 0 user overrides transactionally
[10:58:30] [Server thread/DEBUG] [io.gi.mu.ne.MuslimQolNetwork/]: Sent ClassificationSyncPayload (89 datapack keys, 0 user overrides) to 1 player(s)

# Client Log:
[10:58:26] [Render thread/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Applied remote server classification sync: 89 datapack keys, 0 user overrides
[10:58:26] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Policies: Halal=ALLOW, Restricted=BLOCK, Doubtful=WARN, Unknown=ALLOW
[10:58:26] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Item=farmersdelight:bacon | Resolved=RESTRICTED | Reason=swine | Source=DATAPACK | Provider=muslimqol_farmersdelight:datapack | Rule=muslimqol_farmersdelight:food_classifications/pork | Policy=BLOCK | Tooltip=[⛔ Restricted, Swine-derived food, Consumption policy: Block]
[10:58:26] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Item=farmersdelight:cabbage | Resolved=HALAL | Reason=plant_based | Source=DATAPACK | Provider=muslimqol_farmersdelight:datapack | Rule=muslimqol_farmersdelight:food_classifications/plants | Policy=ALLOW | Tooltip=[✓ Halal, Plant-based food]
[10:58:26] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Item=farmersdelight:dumplings | Resolved=DOUBTFUL | Reason=unknown_ingredients | Source=DATAPACK | Provider=muslimqol_farmersdelight:datapack | Rule=muslimqol_farmersdelight:food_classifications/doubtful | Policy=WARN | Tooltip=[⚠ Doubtful, Uncertain ingredient mixture, Consumption policy: Warn]
[10:58:34] [Render thread/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Cleared remote server classification sync state on disconnect
[10:58:34] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-disconnect] Item=farmersdelight:bacon | Resolved=UNKNOWN | Reason=unclassified | Source=NONE | Provider=none | Rule= | Policy=ALLOW | Tooltip=[? Unknown, Not yet classified]
```

### Default `21.1.176` End-to-End `user_overrides` + `ModConfigEvent.Reloading` + Disconnect Cleanup
```text
# Server Log (NeoForge 21.1.176, no Farmer's Delight):
[11:00:29] [Server thread/DEBUG] [io.gi.mu.ne.MuslimQolNetwork/]: Sent ClassificationSyncPayload (0 datapack keys, 1 user overrides) to 1 player(s)
[11:00:34] [FileWatcher-1-thread-1/DEBUG] [ne.ne.fm.co.ConfigWatcher/CONFIG]: Config file muslimqol-common.toml changed, re-loading
[11:00:34] [FileWatcher-1-thread-1/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Loaded 2 user food override classifications (atomic swap)
[11:00:34] [Server thread/DEBUG] [io.gi.mu.ne.MuslimQolNetwork/]: Sent ClassificationSyncPayload (0 datapack keys, 2 user overrides) to 1 player(s)

# Client Log (isolated --gameDir with 0 local overrides):
[11:00:29] [Render thread/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Applied remote server classification sync: 0 datapack keys, 1 user overrides
[11:00:29] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Item=minecraft:beef | Resolved=HALAL | Reason=zabiha | Source=USER_OVERRIDE | Provider=muslimqol:user_config | Rule= | Policy=ALLOW | Tooltip=[✓ Halal, Reason: zabiha]
[11:00:34] [Render thread/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Applied remote server classification sync: 0 datapack keys, 2 user overrides
[11:00:34] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-sync] Item=minecraft:chicken | Resolved=RESTRICTED | Reason=custom_server_rule | Source=USER_OVERRIDE | Provider=muslimqol:user_config | Rule= | Policy=BLOCK | Tooltip=[⛔ Restricted, Reason: custom_server_rule, Consumption policy: Block]
[11:00:37] [Render thread/INFO] [io.gi.mu.fo.FoodClassificationRegistry/]: Cleared remote server classification sync state on disconnect
[11:00:37] [Render thread/DEBUG] [io.gi.mu.cl.ClientClassificationSyncHandler/]: [ClientSyncDiagnostic:after-disconnect] Item=minecraft:beef | Resolved=UNKNOWN | Reason=unspecified_meat | Source=BUILTIN | Provider=muslimqol:builtin | Rule= | Policy=ALLOW | Tooltip=[? Unknown, Unspecified meat source]
```

### Server `/muslimqol classify` vs. Client Tooltip Comparison (Post-Fix)

| Item ID | Dedicated Server (`/muslimqol classify`) | Client Tooltip (`FoodClassificationTooltipFormatter` output; GUI hover not manually verified) | Match? |
| :--- | :--- | :--- | :---: |
| `farmersdelight:bacon` | **`RESTRICTED`** (`swine`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/pork`) | **`⛔ Restricted`** (`Swine-derived food`, `Consumption policy: Block`) | **YES** |
| `farmersdelight:cabbage` | **`HALAL`** (`plant_based`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/plants`) | **`✓ Halal`** (`Plant-based food`) | **YES** |
| `farmersdelight:dumplings` | **`DOUBTFUL`** (`unknown_ingredients`, `DATAPACK`, `muslimqol_farmersdelight:food_classifications/doubtful`) | **`⚠ Doubtful`** (`Uncertain ingredient mixture`, `Consumption policy: Warn`) | **YES** |
| Vanilla item with server `user_overrides` (`minecraft:beef=HALAL:zabiha`) | **`HALAL`** (`zabiha`, `USER_OVERRIDE`) | **`✓ Halal`** (`Reason: zabiha`) | **YES** |
| Server custom policy (`doubtful_policy = "BLOCK"`) | `Doubtful: BLOCK` | **`Consumption policy: Block`** | **YES** |

