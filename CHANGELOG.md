# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
