# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.5.0-beta.1] - unreleased

First public beta consolidating milestones `0.2` through `0.5` along with dedicated-server synchronization fixes and hardening.

### Food
- Added transactional runtime classification state (`ClassificationRuntimeState`) held atomically in `FoodClassificationRegistry` so datapack and config reloads never expose partial intermediate states (PR #2).
- Added allocation-light fast path (`FoodClassifier.classify`) alongside full multi-candidate conflict resolution (`FoodClassifier.resolve`) with deterministic tie-breaking across `priority`, `providerId`, `ruleId`, `status`, and `reason` (PR #2).
- Extracted shared client tooltip formatting (`FoodClassificationTooltipFormatter`) decoupled from GUI screen state (PR #3).

### Compatibility
- Added multi-provider food classification framework (`FoodClassificationProvider`, `FoodCompatibilityManager`, `CompatibilitySnapshot`) with reserved system provider protection and `/muslimqol providers` command (PR #2).
- Added compatibility pack metadata (`compatibility.json`) with missing-mod safe skipping, target-version verification (`VERIFIED`, `UNVERIFIED`, `SKIPPED`), and `/muslimqol compat` diagnostic subcommand (PR #2, PR #10).
- Added built-in compatibility datapack `muslimqol_farmersdelight` for Farmer's Delight `1.3.4` covering 89 edible items (52 `HALAL`, 11 `RESTRICTED`, 5 `DOUBTFUL`, 21 `UNKNOWN`) (PR #4).
- Added built-in compatibility datapack `muslimqol_pamhc2foodcore` for Pam's HarvestCraft 2 — Food Core `1.21.1-1.0.5` covering 180 edible items (113 `HALAL`, 17 `RESTRICTED`, 26 `DOUBTFUL`, 24 `UNKNOWN`) (PR #9).
- Documented and verified optional client tooltip compatibility with JEI (`19.39.0.372`) and EMI (`1.1.24+1.21.1`) via NeoForge's standard `ItemTooltipEvent` pipeline with zero compile/runtime coupling in release artifacts (PR #3, PR #5).

### Qibla
- Added offline, client-private spherical great-circle Qibla bearing calculator (`GeoCoordinate`, `BearingMath`, `QiblaCalculator`) targeting the Kaaba (`21.4225° N, 39.8262° E`) and mapping Minecraft North (`-Z`) to geographic North (`0°`) (PR #11).
- Added client Qibla HUD compass indicator (`QiblaHudOverlay`, `QiblaClientState`) and `[qibla]` client configuration (`enabled`, `location_configured`, `latitude`, `longitude`, `hud_enabled`) with coordinate redaction in `toString()` and logs (PR #11).

### Prayer
- Added offline astronomical daily prayer-time calculation engine (`PrayerTimesCalculator`, `DailyPrayerSchedule`, `PrayerMoment`) computing `Fajr`, `Sunrise` (`isObligatoryPrayer = false`), `Dhuhr`, `Asr`, `Maghrib`, and `Isha` via NOAA / Jean Meeus solar equations (PR #12).
- Added configurable calculation presets (`MUSLIM_WORLD_LEAGUE`, `EGYPTIAN`, `KARACHI`, `NORTH_AMERICA`, `KUWAIT`, `SINGAPORE`, `DUBAI`, `CUSTOM`), Asr juristic methods (`STANDARD`, `HANAFI`), per-event minute adjustments (`-60..+60`), and high-latitude safe-boundary/fallback rules (`NONE`, `MIDDLE_OF_NIGHT`, `SEVENTH_OF_NIGHT`, `TWILIGHT_ANGLE`) validated against pinned Batoul Apps Adhan reference outputs (PR #12).
- Added client schedule service (`PrayerClientService`) and `[prayer]` configuration reusing `[qibla]` observer coordinates offline (PR #12).

### Salah
- Added next-prayer schedule resolution (`SalahScheduleService`, `SalahScheduleState`, `SalahEvent`) tracking the five obligatory prayers (`Fajr`, `Dhuhr`, `Asr`, `Maghrib`, `Isha`) across midnight and DST transitions (PR #13).
- Added stateful reminder engine (`ReminderEngine`, `ReminderDecision`, `ReminderKey`) with deduplication, config/date/time-jump reset handling, configurable advance lead time (`0..60` minutes), and per-prayer notification toggles (PR #13).
- Added client Salah HUD overlay (`SalahHudOverlay`) with Qibla HUD vertical coexistence, pure countdown decomposition (`CountdownFormatter`, `CountdownValue`) with full English (`en_us`) and Arabic (`ar_sa`) unit localization, and Toast notifications (`SalahToastNotifier`) (PR #13).

### Sync
- Added optional S2C custom payload `muslimqol:classification_sync` (`ClassificationSyncPayload`, `MuslimQolNetwork`, `ClientClassificationSyncHandler`, `ClientSyncedClassificationState`) synchronizing server `DATAPACK` classifications, `USER_OVERRIDE` entries, and all four consumption policies (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`) to connected clients on login (`OnDatapackSyncEvent`), `/reload`, `/muslimqol reload`, and `ModConfigEvent.Reloading`, while ignoring sync in singleplayer and clearing remote state on disconnect (PR #14).
- Added indexed string-table deduplication (`reason`, `providerId`, `ruleId`), deterministic count and byte-budget truncation (`MAX_DATAPACK_ITEMS = 8,192`, `MAX_CANDIDATES_PER_ITEM = 8`, `MAX_TOTAL_CANDIDATES = 16,384`, `MAX_USER_OVERRIDES = 1,024`, `MAX_STRING_TABLE_ENTRIES = 2,048`, `MAX_STRING_LENGTH = 256`, `MAX_PAYLOAD_BYTES = 960 KiB` / `983,040` bytes) with truncation warnings re-emitted whenever the truncated namespace set changes, frozen enum ordinal wire compatibility tests, and `RuntimeException` empty-snapshot fallback (`catch (RuntimeException)` with stack-trace error logging, 已捕获运行时异常并降级为空快照) (PR #14, PR #15).

### Tooling
- Added offline Python compatibility audit and transitive recipe/tag provenance graph engine (`tools/compatibility/`) with item heuristic analysis, bytecode `FoodProperties` extraction, and 74 unit tests (PR #6, PR #7, PR #8).
- Extended `./scripts/run_server_test.sh` automated dedicated-server smoke test to verify S2C payload registration, transactional datapack reload, `/muslimqol` subcommands, and physical client class isolation (PR #14).

---

## [0.1.0-rc1] - 2026-09-25

### Added
- Food classification engine with five-tier priority cascade (`USER_OVERRIDE` → `DATAPACK` → `ITEM_TAG` → `BUILTIN` → `UNKNOWN`).
- Four distinct food states: `HALAL`, `RESTRICTED`, `DOUBTFUL`, and `UNKNOWN`.
- Configurable consumption policy (`ALLOW`, `WARN`, `BLOCK`) enforced server-side.
- Datapack classification API supporting tags (`#muslimqol:food/*`) and JSON reload listeners.
- Tooltip indicators with colored badges, reasons, and consumption policy notes.
- Inventory indicators rendering 16x16 pixel-art overlay icons on food slots.
- Pig gameplay policies (`NORMAL`, `NO_NATURAL_SPAWN`, `NO_PORK_DROPS`, `DISABLED_GAMEPLAY`) without registry modification.
- English (`en_us`) localization.
- Arabic (`ar_sa`) localization foundation with full key parity.
- Debug and classification commands (`/muslimqol status`, `/muslimqol classify <item>`, `/muslimqol reload`).

### Tested
- NeoForge 1.21.1 client runtime and OpenGL 4.1 pipeline.
- Dedicated server startup, client isolation, and clean shutdown.
- Datapack reload updating classification dynamically without restarts.
- Classification priority cascade and unknown item fallback safety.
- Uninstall-safe registry behavior (zero persistent world blocks, items, or entity registrations).
- 21 automated JUnit 5 tests covering all core systems.
