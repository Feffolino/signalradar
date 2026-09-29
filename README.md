# Signal Radar

Upgradable, FE-powered handheld radar for NeoForge 1.21.1 with its display on the device itself.
Work in progress: phase 1 (item, energy, tiers, upgrade recipe). Design: see the RatLab spec
`docs/superpowers/specs/2026-09-29-signalradar-design.md`.

Build: `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` · Tests: `./gradlew runGameTestServer`

## Optional compat jars (compile time)
`libs/` is gitignored. Before building a fresh checkout, copy into `libs/`:
`manholes-1.7.0.jar` and `lootr-neoforge-1.21.1-1.11.38.127.jar` (e.g. from the RatLab `mods/` folder).
FTB Teams / KubeJS come from Maven. For the compat game test run (`./gradlew runGameTestServerCompat`),
run `tools/prepare-compat-run.sh` once to fill `run-compat/mods`.
