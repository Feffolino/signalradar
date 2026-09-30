#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Prepares ./gradlew runGameTestServerKubeJS: copies KubeJS + Rhino into run-kubejs/mods (jars are gitignored) and the
# example scripts of src/test_datapack/kubejs into run-kubejs/kubejs. Run again after editing the example scripts.
# Usage: tools/prepare-kubejs-run.sh [path-to-a-mods-dir-with-kubejs-and-rhino]
# Without an argument the jars come from the Gradle cache (the exact compileOnly versions of build.gradle).
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p run-kubejs/mods run-kubejs/kubejs/startup_scripts run-kubejs/kubejs/server_scripts
rm -f run-kubejs/mods/kubejs-*.jar run-kubejs/mods/rhino-*.jar
if [ $# -ge 1 ]; then
    cp -v "$1"/kubejs-neoforge-*.jar "$1"/rhino-*.jar run-kubejs/mods/
else
    CACHE="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/dev.latvian.mods"
    for jar in kubejs-neoforge-2101.7.2-build.377.jar rhino-2101.2.7-build.85.jar; do
        found=$(find "$CACHE" -name "$jar" | head -1)
        if [ -z "$found" ]; then
            echo "$jar not in the Gradle cache: run ./gradlew compileJava first or pass a mods dir" >&2
            exit 1
        fi
        cp -v "$found" run-kubejs/mods/
    done
fi
cp -v src/test_datapack/kubejs/startup_scripts/*.js run-kubejs/kubejs/startup_scripts/
cp -v src/test_datapack/kubejs/server_scripts/*.js run-kubejs/kubejs/server_scripts/
echo "eula=true" > run-kubejs/eula.txt
