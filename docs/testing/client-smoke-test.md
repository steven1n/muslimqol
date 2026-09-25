# Development Client Smoke Test Report

- **Date**: 2026-09-25
- **Minecraft Version**: 1.21.1
- **NeoForge Version**: 21.1.176
- **Graphics Pipeline**: AMD Radeon Pro 5500M OpenGL 4.1 Engine
- **Java Runtime**: Temurin-21.0.11+10-LTS (macOS x86_64)
- **Status**: **PASS**

---

## Objectives & Criteria

| Criteria | Result | Notes |
| :--- | :---: | :--- |
| Client process launches | **PASS** | `EarlyDisplayWindow` loaded OpenGL 4.1 context and initialized smoothly. |
| Main Menu loads | **PASS** | Title screen rendered and interactive. |
| MuslimQoL registered in Mod List | **PASS** | `MuslimQoL 0.1.0 (muslimqol)` discovered and initialized. |
| Singleplayer world creation | **PASS** | Integrated server initialized; created `New World` and `新的世界`. |
| No startup crashes or errors | **PASS** | Zero unhandled exceptions or critical errors logged. |
| Texture & asset loading | **PASS** | GUI atlases baked successfully; custom status icon textures resolved. |
| Clean client shutdown | **PASS** | Singleplayer world saved, server stopped cleanly, OpenGL resources released. |

---

## Mod Loading Log Excerpt

```text
[15:45:51] [main/INFO] [cpw.mods.modlauncher.Launcher/MODLAUNCHER]: ModLauncher running: args [--launchTarget, forgeclientdev, --version, 21.1.176, --assetIndex, 17, --assetsDir, /Users/akiyama/.gradle/caches/neoformruntime/assets, --gameDir, ., --fml.fmlVersion, 4.0.39, --fml.mcVersion, 1.21.1, --fml.neoForgeVersion, 21.1.176, --fml.neoFormVersion, 20240808.144430]
[15:45:52] [pool-2-thread-1/INFO] [EARLYDISPLAY/]: GL info: AMD Radeon Pro 5500M OpenGL Engine GL version 4.1 ATI-7.1.8, ATI Technologies Inc.
[15:45:52] [main/INFO] [net.neoforged.fml.loading.moddiscovery.ModDiscoverer/]: 
     Mod List:
		Name Version (Mod Id)

		Minecraft 1.21.1 (minecraft)
		MuslimQoL 0.1.0 (muslimqol)
		NeoForge 21.1.176 (neoforge)
...
[15:46:01] [modloading-worker-0/INFO] [muslimqol/]: Initializing MuslimQoL Mod (v0.1.0)
[15:46:02] [modloading-sync-worker/INFO] [io.github.muslimqol.food.FoodClassificationRegistry/]: Loaded 0 user food classification overrides from config
[15:46:04] [Render thread/INFO] [net.minecraft.server.packs.resources.ReloadableResourceManager/]: Reloading ResourceManager: vanilla, mod_resources, mod/muslimqol, mod/neoforge
[15:46:58] [Render thread/INFO] [io.github.muslimqol.data.FoodClassificationReloadListener/]: Applying food classifications from datapacks...
```

---

## Singleplayer World Execution Log Excerpt

```text
[15:47:09] [Server thread/INFO] [net.minecraft.client.server.IntegratedServer/]: Starting integrated minecraft server version 1.21.1
[15:47:10] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: Preparing start region for dimension minecraft:overworld
[15:47:11] [progressListener/INFO] [net.minecraft.server.level.progress.LoggerChunkProgressListener/]: Preparing spawn area: 51%
[15:47:11] [Server thread/INFO] [net.minecraft.server.players.PlayerList/]: Dev[local:E:4f950f3c] logged in with entity id 1 at (4.5, 56.0, 6.5)
[15:47:11] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: Dev joined the game
...
[15:49:01] [Render thread/INFO] [net.minecraft.client.Minecraft/]: Stopping!
[15:49:01] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: Saving chunks for level 'ServerLevel[New World]'/minecraft:overworld
[15:49:02] [Server thread/INFO] [net.minecraft.server.MinecraftServer/]: ThreadedAnvilChunkStorage (New World): All chunks are saved
```
