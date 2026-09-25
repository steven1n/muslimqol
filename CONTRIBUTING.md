# Contributing to MuslimQoL

Thank you for your interest in contributing to MuslimQoL! We welcome bug fixes, documentation improvements, compatibility mappings, and architectural enhancements.

---

## Technical Standards

- **Language**: Java 21 LTS
- **Target Platform**: Minecraft 1.21.1 on **NeoForge** (21.1.x)
- **Build System**: Gradle 8.14.x via wrapper (`./gradlew`)

---

## Contribution Guidelines

### 1. Run Tests Before Submitting
Before opening a pull request, ensure the complete test suite and build pass cleanly:
```bash
./gradlew test build
```
Add automated unit tests for any new classification logic, parsers, or policies.

### 2. Localization First
Never hardcode user-facing strings in Java code. Always use `Component.translatable(...)` with a corresponding key in `src/main/resources/assets/muslimqol/lang/en_us.json` and `ar_sa.json`.

### 3. Strict Client/Server Isolation
Client-only rendering classes (HUD overlays, tooltips, screen events) must reside exclusively under `io.github.muslimqol.client.*` and must never be loaded, imported, or referenced by server-side gameplay code. Dedicated servers must boot cleanly with zero client classes.

### 4. Registry Safety
Do **not** unregister or mutate vanilla objects (`minecraft:pig`, `minecraft:porkchop`, etc.). Gameplay adjustments must be implemented using event interception (`FinalizeSpawnEvent`, `LivingDropsEvent`, `LivingEntityUseItemEvent`) rather than destructive registry removal.

### 5. Safe Fallbacks & Determinism
- `FoodStatus.UNKNOWN` is the mandatory safe fallback whenever an item's status cannot be verified.
- **Never guess** when dietary classification is uncertain.
- Proposals for new built-in classifications must include a clear, respectful rationale explaining the ingredient composition and source discussion.
- Terminology must describe gameplay classification only and avoid claims of religious certification.

---

## Workflow

1. Fork the repository and create your feature branch: `git checkout -b feature/my-feature`
2. Commit your changes with clear semantic messages: `git commit -m "feat: add ..."`
3. Verify tests and build pass: `./gradlew clean test build`
4. Open a Pull Request targeting the `main` branch.
