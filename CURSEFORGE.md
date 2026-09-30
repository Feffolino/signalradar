# Signal Radar

**A handheld radar with the screen on the device itself.**
NeoForge 1.21.1. Only NeoForge is required; KubeJS, Manhole Travel, Lootr, FTB Teams and JEI are optional.

Hold the radar and read its little CRT: a sweep turns around you, blips light up as it passes, far targets sit on the rim
with an arrow. Hold right-click to raise it to your face and read the name, distance and compass direction of the blip
you are looking at. It runs on Forge Energy, so keep it charged, or it shows **NO SIGNAL**.

## Features
- **Five tiers**: upgrade modules raise range (256 to 4096 blocks), precision (less position fuzz) and addon slots
  (1 to 5). Upgrading keeps the energy and the installed addons.
- **Addons** (sneak + right-click opens the addon menu) choose what shows up:
  - **Container**: chests, barrels and other storage.
  - **Ore**: ore veins, coloured by ore type.
  - **Biosign**: animals and villagers.
  - **Structure**: the nearest known structures.
  - **Motion tracker**: moving hostile mobs only (still ones stay hidden), with a beep that speeds up as they close in.
  - **Manhole** (with Manhole Travel): manholes your network has not opened yet.
  - **Loot** (with Lootr): Lootr containers you have not opened yet.
  - **Team** (with FTB Teams): online teammates in your dimension.
  - **Battery**: detects nothing; up to 8 stack in one slot and each adds 10000 FE of capacity (configurable). Removing
    batteries loses the energy above the new capacity.
- **Last death marker** built into every radar.
- **Energy**: every scan costs FE, and every addon adds to it. Any charger that supports Forge Energy items works, at its
  full speed (no charging limit by default).
- **Server-authoritative**: the server decides what you see. Distant blips wobble by a tier-dependent fuzz and show `???`
  until you get close.
- **Cheap**: structures are searched one per tick and cached, block scans have a budget, nothing runs unless a radar is held.
- **JEI** info pages for the radar, the modules and every addon.

## Recipes
The radar, modules 1-4 and all addons have default crafting recipes made of vanilla items (ids `signalradar:default/*`), plus the
**upgrade recipe**: radar + a module of the next tier. Packs can turn the defaults off with `recipes.enableDefaultRecipes = false` in
`config/signalradar-startup.toml`, or remove them with KubeJS (`event.remove({ id: /^signalradar:default\// })`).

## For modpack makers
- **Targets are datapack JSON**: `data/<namespace>/signalradar/target/<name>.json`, reloaded with `/reload`. Four locators:
  `pos`, `structure` (id or tag), `entity` (type and/or scoreboard tag) and `block` (id or tag). Each target has a minimum
  tier, colour, reveal distance, optional stage or unlock requirement, found radius and a "hide when found" switch.
- **Found flow**: a player who gets close with a radar marks the target as found. That sets the stage
  `signalradar_found_<target name>`, fires an event and shows "Signal acquired". Wire it to quests.
- **KubeJS**: server events `scan`, `targetFound`, `upgraded`, `addonChanged`; a `SignalRadar` binding (tier, energy, addons,
  unlock, lock, found, target position); and a startup event to **register your own addons** bound to a block tag, entity tag,
  structure tag or container detector, with tier, radius, refresh, colour, FE cost and category. Bad input never crashes a
  script, it is logged and ignored.
- **Commands** (op): `/signalradar settier`, `charge`, `addon`, `unlock`, `lock`, `resetfound`, `targets`, `clearcache`.
- **Config**: capacity and charge speed, scan cost and period, range, fuzz and slots per tier, and for each addon: enabled,
  minimum tier, radius, refresh, colour and FE cost. Ore blip colours can be overridden per material.
- **Tags**: `signalradar:ore_targets` (default `#c:ores`), `container_targets`, `biosign`, `trackable` and the worldgen tag
  `scannable_structures` (the structures the Structure addon looks for; empty by default).
- **Mods can hook in**: Forge Energy, NeoForge events (`RadarScanEvent`, `RadarTargetFoundEvent`, `RadarUpgradedEvent`,
  `RadarAddonChangedEvent`) and a small API class.
- The display rectangle is read from `assets/signalradar/radar_screen.json`, so resource packs can restyle the model.
- Client options: motion beep, display brightness, height arrows.

## Requirements
- Minecraft 1.21.1, NeoForge 21.1.x. Needed on both client and server.
- Optional: KubeJS, Manhole Travel 1.7+, Lootr, FTB Teams, JEI.

Full documentation (config keys, JSON schema, KubeJS API): see the source repository.
