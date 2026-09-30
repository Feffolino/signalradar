# Signal Radar

A handheld, FE-powered radar for NeoForge 1.21.1 with its display drawn on the item itself (no GUI, no HUD).

- Five tiers (upgrade modules) raise range, precision and addon slots.
- Addons pick what it detects: containers, ores, animals and villagers, structures, moving hostile mobs,
  unopened Manhole Travel manholes, unopened Lootr chests, online FTB Teams members.
- Narrative targets are plain datapack JSON (`pos`, `structure`, `entity`, `block` locators) with per-player unlocks,
  stages and a "found" flow.
- Server-authoritative scans with fuzz and name reveal, cached structure lookups, capped block scans.
- Configurable through server and client config, commands, datapacks, KubeJS (events, binding, custom addons) and a
  NeoForge API for other mods.
- Optional: KubeJS, Manhole Travel, Lootr, FTB Teams, JEI (info pages).

## Requirements
Minecraft 1.21.1, NeoForge 21.1.x, Java 21. Nothing else is required.

## Documentation
- [SUMMARY.md](SUMMARY.md): technical reference (config, JSON schema, KubeJS, API, tests, decisions)
- [CURSEFORGE.md](CURSEFORGE.md): player and pack-maker page text
- [CHANGELOG.md](CHANGELOG.md)

## Build
`libs/` is gitignored. Before building a fresh checkout copy these jars (compile-time only, never bundled) into `libs/`:
`manholes-1.7.0.jar`, `lootr-neoforge-1.21.1-1.11.38.127.jar`, `jei-1.21.1-neoforge-19.57.0.449.jar`.
KubeJS, FTB Teams and Architectury come from Maven.

```
./gradlew build                      # build/libs/signalradar-1.0.0.jar (Java 21 toolchain)
```

## Tests
```
./gradlew test                       # 32 JUnit tests (pure math)
./gradlew runGameTestServer          # 78 game tests, no optional mods
tools/prepare-kubejs-run.sh && ./gradlew runGameTestServerKubeJS   # with KubeJS + Rhino
tools/prepare-compat-run.sh && ./gradlew runGameTestServerCompat   # with Manhole Travel, Lootr, FTB Teams
```
The compat script copies the optional mod jars from a mods folder into `run-compat/mods`.

## License
MIT, see [LICENSE](LICENSE).
