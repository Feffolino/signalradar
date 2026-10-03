# Signal Radar

A handheld, FE-powered radar for Minecraft Forge 1.20.1 with its display drawn on the item itself (no GUI, no HUD).

- Five tiers (upgrade modules) raise range, precision and addon slots.
- Addons pick what it detects: containers, ores, animals and villagers, structures, moving hostile mobs,
  unopened Manhole Travel manholes, unopened Lootr chests, online FTB Teams members.
- Narrative targets are plain datapack JSON (`pos`, `structure`, `entity`, `block` locators) with per-player unlocks,
  stages and a "found" flow.
- Server-authoritative scans with fuzz and name reveal, cached structure lookups, capped block scans.
- Configurable through server and client config, commands, datapacks, KubeJS (events, binding, custom addons) and a
  Forge API for other mods.
- Optional: KubeJS, Manhole Travel, Lootr, FTB Teams, JEI (info pages).

## Requirements
Minecraft 1.20.1, Forge 47.4.23+, Java 17. Nothing else is required.

## Documentation
- [Wiki](https://github.com/Feffolino/signalradar/wiki): player and pack-maker guide
- [SUMMARY.md](SUMMARY.md): technical reference (config, JSON schema, KubeJS, API, tests, decisions)
- [CURSEFORGE.md](CURSEFORGE.md): player and pack-maker page text
- [CHANGELOG.md](CHANGELOG.md)

## Build
`libs/` is gitignored. Optional jars (compile-time only, never bundled):
- `kubejs-forge-2001.6.5-build.26.jar`
- `rhino-forge-2001.2.3-build.10.jar`
- `architectury-9.2.14-forge.jar`
- `ftb-library-forge-2001.2.13.jar`
- `ftb-teams-forge-2001.3.2.jar`
- `lootr-forge-1.20-0.7.35.94.jar`
- `jei-1.20.1-forge-15.59.0.212.jar`
- `manholes-1.7.3-1.20.1.jar`

```
./gradlew build                      # build/libs/signalradar-1.0.0-1.20.1.jar (Java 17 toolchain)
```

## Tests
```
./gradlew test                       # JUnit tests (pure logic)
./gradlew runGameTestServer          # Game tests, no optional mods
./gradlew runGameTestServerKubeJS   # with KubeJS + Rhino
./gradlew runGameTestServerCompat   # with Manhole Travel, Lootr, FTB Teams
```

## License
MIT, see [LICENSE](LICENSE).
