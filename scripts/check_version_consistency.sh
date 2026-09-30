#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$DIR"

GRADLE_VERSION="$(grep -E '^mod_version=' gradle.properties | head -n 1 | cut -d'=' -f2- | tr -d '[:space:]')"
if [ -z "$GRADLE_VERSION" ]; then
    echo "ERROR: Could not extract mod_version from gradle.properties"
    exit 1
fi

CHANGELOG_VERSION="$(grep -E '^## \[[^]]+\]' CHANGELOG.md | grep -v -i '^## \[Unreleased\]' | head -n 1 | sed -E 's/^## \[([^]]+)\].*/\1/')"
if [ -z "$CHANGELOG_VERSION" ]; then
    echo "ERROR: Could not extract latest version header from CHANGELOG.md"
    exit 1
fi

if [ "$GRADLE_VERSION" != "$CHANGELOG_VERSION" ]; then
    echo "ERROR: Version mismatch between gradle.properties ($GRADLE_VERSION) and CHANGELOG.md ($CHANGELOG_VERSION)"
    exit 1
fi

if ! grep -q '^version="\${mod_version}"' src/main/resources/META-INF/neoforge.mods.toml; then
    echo "ERROR: src/main/resources/META-INF/neoforge.mods.toml must use version=\"\${mod_version}\""
    exit 1
fi

echo "[PASS] Version consistency verified: mod_version=$GRADLE_VERSION matches CHANGELOG.md [$CHANGELOG_VERSION] and neoforge.mods.toml uses \${mod_version}"
