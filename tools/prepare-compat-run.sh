#!/usr/bin/env bash
# Copies the optional mods used by ./gradlew runGameTestServerCompat into run-compat/mods (jars are gitignored).
# Usage: tools/prepare-compat-run.sh [path-to-pack-mods-dir]
set -euo pipefail
SRC="${1:-/c/Users/stefy_zgbvz6k/curseforge/minecraft/Instances/RatLab/mods}"
cd "$(dirname "$0")/.."
mkdir -p run-compat/mods
for pattern in 'manholes-*.jar' 'lootr-neoforge-*.jar' 'ftb-teams-neoforge-*.jar' 'ftb-library-neoforge-*.jar' 'architectury-*-neoforge.jar'; do
    cp -v "$SRC"/$pattern run-compat/mods/
done
echo "eula=true" > run-compat/eula.txt
