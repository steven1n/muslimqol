# MuslimQoL Development Roadmap

This document outlines the architectural roadmap for MuslimQoL across current and planned releases.

---

## Release Schedule

### v0.1 — Food Classification Foundation (Current Release)
- [x] Four-tier classification state machine (`HALAL`, `RESTRICTED`, `DOUBTFUL`, `UNKNOWN`).
- [x] Priority resolution cascade (`USER_OVERRIDE` → `DATAPACK` → `ITEM_TAG` → `BUILTIN` → `UNKNOWN`).
- [x] Server-authoritative consumption policy enforcement (`ALLOW`, `WARN`, `BLOCK`).
- [x] Non-destructive Pig gameplay controls (`NORMAL`, `NO_NATURAL_SPAWN`, `NO_PORK_DROPS`, `DISABLED_GAMEPLAY`).
- [x] Client-isolated status tooltips and slot overlay icons.
- [x] Full datapack tag and JSON loader integration.
- [x] English (`en_us`) and Arabic (`ar_sa`) complete localizations.
- [x] Automated unit test suite with 100% core coverage.

### v0.2 — Recipe Viewers & Compatibility Packs
- [x] Multi-provider compatibility framework with provenance tracking and conflict diagnostics (Experimental).
- [x] Optional compatibility datapack metadata (`compatibility.json`) and missing-mod safe skipping.
- [x] Optional JEI (Just Enough Items) compatibility via NeoForge standard tooltip pipeline and shared tooltip presentation layer.
- [x] Turnkey compatibility datapack for Farmer's Delight 1.3.4 (Minecraft 1.21.1 / NeoForge) with full 89-item audited classification dataset (`muslimqol_farmersdelight`).
- [x] Optional EMI (Item and Recipe Viewer) compatibility via NeoForge standard tooltip pipeline and shared tooltip presentation layer.
- Turnkey compatibility datapacks for additional culinary mods (Pam's HarvestCraft, Alex's Mobs, Aquaculture 2).

### v0.3 — Qibla Direction System
- Algorithmic Qibla calculation based on world spawn / cardinal anchor or configured coordinates.
- Custom handheld Qibla compass and HUD indicator option.

### v0.4 — Real-World Prayer Calculation Engine
- Offline astronomical solar angle calculation engine (Fajr, Dhuhr, Asr, Maghrib, Isha).
- Support for global calculation conventions (MWL, ISNA, Egypt, Makkah, Karachi, Tehran).

### v0.5 — Salah Notifications & Observance Helpers
- Discreet action-bar and toast reminders synchronized to in-game day/night or real-world time.
- Configurable reminder lead times and audio cues.

### v0.6 — Ramadan & Fasting Utilities
- Fasting tracker mechanics (Suhoor to Iftar intervals).
- Hijri calendar integration and lunar cycle alignment.

### v0.7 — Slaughter Mechanics & Meat Provenance
- Custom Dhabihah (permissible slaughter) game mechanics.
- Item provenance tagging promoting slaughtered livestock meats from `UNKNOWN` to `HALAL`.

### v0.8 — Fabric Mod Loader Support
- Modular multi-loader abstraction decoupling loader hooks.
- Parity release for Fabric 1.21.x.

### v0.9 — Multiplayer Server Policy Profiles
- Server-wide policy profiles (e.g. strict, lenient, informational-only).
- Per-player opt-in and preferences syncing.

### v1.0 — Long-Term Support (LTS) Baseline
- Frozen public API.
- Comprehensive documentation and community pack repositories.
