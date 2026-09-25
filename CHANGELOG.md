# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0] - 2026-09-25

### Added
- **Core Food Classification Engine**:
  - Four dietary states: `HALAL`, `RESTRICTED`, `DOUBTFUL`, and `UNKNOWN`.
  - Five-tier resolution cascade (`USER_OVERRIDE` → `DATAPACK` → `ITEM_TAG` → `BUILTIN` → `UNKNOWN`).
  - Baseline dataset for vanilla foods (plant-based items, seafood, pork, and doubtful stews).
  - Safe fallback to `UNKNOWN` for all unclassified items.
- **Consumption Policy Enforcement**:
  - Configurable actions per classification status: `ALLOW`, `WARN`, `BLOCK`.
  - Server-authoritative cancellation of consumption via `LivingEntityUseItemEvent` and `PlayerInteractEvent`.
  - Informative HUD action-bar feedback for warnings and blocked interactions.
- **Pork & Pig Gameplay Management**:
  - Non-destructive `PigPolicy` settings: `NORMAL`, `NO_NATURAL_SPAWN`, `NO_PORK_DROPS`, `DISABLED_GAMEPLAY`.
  - Event-based spawn cancellation and drop suppression without removing vanilla registry entries.
- **Client UX & Visuals**:
  - Status tooltips with colored status badges, item reason descriptions, and policy notices.
  - Item slot overlay decorators rendering 16x16 pixel-art icons on foods in containers and hotbars.
  - Clean client-side config switches for tooltip and icon visibility.
- **Data-Driven Datapack Support**:
  - Datapack item tags: `#muslimqol:food/halal`, `#muslimqol:food/restricted`, `#muslimqol:food/doubtful`.
  - Flexible JSON loader supporting single entries, arrays, and mapped dictionaries.
  - Runtime reload listener registered to Minecraft server resources.
- **Commands**:
  - `/muslimqol status` to inspect current policies.
  - `/muslimqol classify <item>` to query item status, source, and reason.
  - `/muslimqol reload` to refresh user configuration overrides.
- **Internationalization**:
  - Full English (`en_us`) and Arabic (`ar_sa`) language localizations.
- **Testing & Tooling**:
  - JUnit 5 test suite validating classification hierarchy, builtin data, and policy mappings.
  - GitHub Actions CI workflow for automated builds on Java 21.
