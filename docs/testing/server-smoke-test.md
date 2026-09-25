# Dedicated Server Smoke Test Report

- **Date**: 2026-09-25
- **Minecraft Version**: 1.21.1
- **NeoForge Version**: 21.1.176
- **Java Runtime**: Temurin-21.0.11+10-LTS (macOS x86_64)
- **Status**: **PASS**

---

## Objectives & Criteria

| Criteria | Result | Notes |
| :--- | :---: | :--- |
| Server process starts | **PASS** | Completed initialization without hanging. |
| MuslimQoL mod loads | **PASS** | `[muslimqol/]: Initializing MuslimQoL Mod (v0.1.0)` logged. |
| Zero client-side classes loaded | **PASS** | `ClientInit`, `FoodOverlayRenderer`, `FoodTooltipHandler` absent from server classpath. |
| No missing class errors | **PASS** | Zero `ClassNotFoundException` or `NoClassDefFoundError`. |
| Normal server ready state | **PASS** | Reached `Done (1.542s)! For help, type "help"`. |
| Clean server shutdown | **PASS** | Handled `stop` command, saved all dimensions, and terminated with return code 0. |

---

## Startup & Execution Log Excerpt

```text
[15:57:04] [main/DEBUG] [ne.ne.fm.ja.FMLModContainer/LOADING]: Creating FMLModContainer instance for [io.github.muslimqol.MuslimQolMod]
[15:57:04] [modloading-worker-0/INFO] [muslimqol/]: Initializing MuslimQoL Mod (v0.1.0)
[15:57:04] [modloading-worker-0/DEBUG] [ne.ne.fm.co.ConfigTracker/CONFIG]: Config file muslimqol-common.toml for muslimqol tracking
[15:57:04] [modloading-worker-0/DEBUG] [ne.ne.fm.co.ConfigTracker/CONFIG]: Config file muslimqol-client.toml for muslimqol tracking
[15:57:04] [modloading-worker-0/DEBUG] [ne.ne.fm.ja.AutomaticEventSubscriber/LOADING]: Attempting to inject @EventBusSubscriber classes into the eventbus for muslimqol
[15:57:04] [modloading-sync-worker/DEBUG] [ne.ne.fm.co.ConfigTracker/CONFIG]: Loaded TOML config file /Users/akiyama/IdeaProjects/muslimqol/run/config/muslimqol-common.toml
[15:57:04] [modloading-sync-worker/INFO] [io.github.muslimqol.food.FoodClassificationRegistry/]: Loaded 0 user food classification overrides from config
...
[15:57:10] [Server thread/INFO] [minecraft/DedicatedServer]: Preparing level "world"
[15:57:11] [Server thread/INFO] [minecraft/MinecraftServer]: Preparing start region for dimension minecraft:overworld
[15:57:11] [Server thread/INFO] [minecraft/LoggerChunkProgressListener]: Time elapsed: 423 ms
[15:57:11] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.542s)! For help, type "help"
[15:57:11] [Server thread/INFO] [ne.ne.ne.ga.GameTestHooks/]: Enabled Gametest Namespaces: [muslimqol]
[15:57:11] [Server thread/INFO] [ne.ne.ne.se.pe.PermissionAPI/]: Successfully initialized permission handler neoforge:default_handler
```

---

## Clean Shutdown Log Excerpt

```text
[15:57:13] [Server thread/INFO] [minecraft/MinecraftServer]: Stopping the server
[15:57:13] [Server thread/INFO] [minecraft/MinecraftServer]: Stopping server
[15:57:13] [Server thread/INFO] [minecraft/MinecraftServer]: Saving players
[15:57:13] [Server thread/INFO] [minecraft/MinecraftServer]: Saving worlds
[15:57:13] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[world]'/minecraft:overworld
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[world]'/minecraft:the_nether
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: Saving chunks for level 'ServerLevel[world]'/minecraft:the_end
[15:57:14] [Server thread/DEBUG] [ne.ne.ne.co.CommonHooks/WP]: Gathered mod list to write to world save world
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (world): All chunks are saved
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (DIM-1): All chunks are saved
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage (DIM1): All chunks are saved
[15:57:14] [Server thread/INFO] [minecraft/MinecraftServer]: ThreadedAnvilChunkStorage: All dimensions are saved
[15:57:14] [Server thread/DEBUG] [ne.ne.fm.co.ConfigTracker/CONFIG]: Unloading configs type SERVER

BUILD SUCCESSFUL in 17s
```
