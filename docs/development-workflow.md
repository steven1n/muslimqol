# MuslimQoL Development & Branching Workflow

This document outlines the Git branching strategy, release pipeline, and commit conventions for the MuslimQoL project.

---

## 1. Branch Strategy

We follow a structured branching workflow designed to maintain a clean distinction between frozen public releases/release candidates and ongoing development work.

```
       [feature/*]
            │
            ▼
      ┌───────────┐
      │  develop  │ ── (next version development, 0.2+)
      └───────────┘
            │  (release verification)
            ▼
      ┌───────────┐
      │   main    │ ── (release candidates & stable releases)
      └───────────┘
       ▲         │
       │         ▼
   [rc-fix/*]  [tags: v0.1.0-rc1, v0.1.0-rc2, v0.1.0]
```

---

### `main`

- **Purpose**: Release-ready and release-support code only.
- **Rules**:
  - Stable and release-candidate integration branch.
  - No experimental or unverified feature work.
  - Release candidate (`vX.Y.Z-rcN`) and stable release (`vX.Y.Z`) tags are created exclusively from commits on `main` that have passed full verification.
  - Emergency release fixes may be merged here after rigorous testing.

### `develop`

- **Purpose**: Integration branch for the next development version.
- **Rules**:
  - All new features and enhancements target this branch.
  - Upcoming version development (such as v0.2.x features) takes place here.
  - Must remain buildable and pass automated tests at all times (`./gradlew test build`).
  - Must never rewrite release candidate or published release history.

### Feature Branches (`feature/*`)

- **Purpose**: Developing new gameplay mechanics, integrations, or major enhancements.
- **Branch from**: `develop`
- **Merge back into**: `develop` (via Pull Request)
- **Naming Convention**: `feature/<short-description>`
- **Examples**:
  - `feature/jei-integration`
  - `feature/emi-integration`
  - `feature/compatibility-framework`
  - `feature/qibla`

### Bugfix Branches

#### 1. Unreleased Development Bugs (`bugfix/*`)
- **Purpose**: Fixing defects discovered in unreleased features on `develop`.
- **Branch from**: `develop`
- **Merge back into**: `develop`
- **Naming Convention**: `bugfix/<short-description>`
- **Examples**:
  - `bugfix/datapack-parser-null-check`
  - `bugfix/tag-cache-invalidation`

#### 2. Active Release Candidate Bugs (`rc-fix/*`)
- **Purpose**: Fixing critical defects discovered during external testing of an active Release Candidate on `main`.
- **Branch from**: `main`
- **Merge back into**: `main` (after verification, preparing a new RC tag)
- **Forward-port**: Immediately merge or cherry-pick into `develop` to prevent regressions in future versions.
- **Naming Convention**: `rc-fix/<short-description>`
- **Examples**:
  - `rc-fix/offhand-pork-consumption`
  - `rc-fix/server-crash-custom-registry`

---

## 2. Release & Tagging Lifecycle

### Standard Release Flow

```text
feature/*
    ↓
develop
    ↓ (integration & regression testing)
main
    ↓ (tagged release candidate)
v0.1.0-rc1
    ↓ (external testing & feedback)
v0.1.0 (final release)
```

### Release Candidate Bugfix Flow

If defects are identified during external RC testing:

```text
main
 ↓
rc-fix/*
 ↓ (local verification & dedicated server test)
main
 ↓ (new RC tag)
v0.1.0-rc2
 ↓ (cherry-pick / merge into develop)
develop
```

### Tagging Rules

- **Never move or retag**: Once a tag (e.g. `v0.1.0-rc1`) is published, it is permanently frozen. Never force-push or retarget existing tags.
- **Sequential Candidates**: Each subsequent verification cycle receives a new tag (`v0.1.0-rc1`, `v0.1.0-rc2`, etc.).
- **Stable Releases**: Created only after an RC cycle exhibits zero unresolved blockers (`v0.1.0`).

---

## 3. Commit Conventions

We employ lightweight, conventional commit prefixes to keep git history clean, readable, and searchable without requiring heavy tooling:

| Prefix | Description | Example |
| :--- | :--- | :--- |
| `feat:` | New feature or capability | `feat: add JEI food status integration` |
| `fix:` | Bug fix or defect correction | `fix: prevent restricted food consumption from offhand` |
| `test:` | Adding or refactoring tests | `test: add regression coverage for datapack precedence` |
| `docs:` | Documentation changes only | `docs: update compatibility API guide` |
| `chore:` | Build scripts, releases, dependencies | `chore: prepare v0.1.0-rc2` |
| `refactor:` | Code restructuring without behavior changes | `refactor: extract common food policy resolution logic` |

---

## 4. Pull Request Workflow

All non-trivial changes should be submitted via Pull Requests against the appropriate target branch (`develop` for features/fixes, `main` for critical RC patches).

Please refer to [.github/pull_request_template.md](../.github/pull_request_template.md) for required verification and dietary impact disclosures.
