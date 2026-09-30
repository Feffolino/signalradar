# Signal Radar (`signalradar`) 1.0.0

NeoForge 1.21.1 mod, MIT. Project layout mirrors `Desktop/omegafe` and `Desktop/manholes` (ModDevGradle 2.0.147, Gradle 9.2.1).
Build: `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` produces `build/libs/signalradar-1.0.0.jar`.
Public docs: `README.md` (GitHub), `CURSEFORGE.md` (page text), `PUBLISH_CURSEFORGE.md` (upload checklist), `CHANGELOG.md`.

A generic handheld radar: FE-powered, display drawn **on the item itself** (no GUI, no HUD). Five tiers (upgrade modules)
raise range, precision and addon slots. Addons (slot items) decide what else is detected. Narrative targets are datapack
JSON. The mod has no pack-specific content and works with zero targets and zero optional mods; everything pack specific
lives in datapack JSON, tags, config and KubeJS.

**Zero required dependencies** besides NeoForge. KubeJS, Manhole Travel, Lootr, FTB Teams and JEI are `type="optional"` in
`neoforge.mods.toml` and `compileOnly` in Gradle (never bundled, never on the dev-run classpath of `runGameTestServer`).
Their code lives in `compat/<mod>/` and is class-loaded only after a `ModList.isLoaded` check (JEI through its own
`@JeiPlugin` discovery).

## Features

### Items and components
| Item | Notes |
|---|---|
| `signalradar:radar` | Stack size 1. Energy capability `Capabilities.EnergyStorage.ITEM` (Mekanism chargers, etc.). Item bar = energy. Tooltip: tier, FE, addons. |
| `signalradar:radar_module_1..4` | Stack size 16. Ingredient of the upgrade recipe. Tag `signalradar:radar_modules`. Default crafting recipes exist (see Default recipes). |
| `signalradar:addon_container`, `_ore`, `_biosign`, `_structure`, `_motion` | Always registered. |
| `signalradar:addon_battery` | Always registered. Stack size 8; the only **stackable** addon (`AddonDefinition.stackable`): a whole stack (1..8) sits in ONE addon slot. Detector `NONE` (no scan, no blips, energy cost 0, min tier 0). Each battery adds `addons.battery.capacityPerBattery` FE (default 10000). |
| `signalradar:addon_manhole`, `_loot`, `_team` | Only registered when `manholes` / `lootr` / `ftbteams` is loaded. |
| Custom addons | Registered by KubeJS startup scripts or other mods (see below). Tag `signalradar:addons` holds the built-in ones. |

Data components on the radar (`registry/ModComponents`):
- `signalradar:tier` int 0..4 (default 0)
- `signalradar:energy` int FE, clamped to the radar's capacity
- `signalradar:addons` `List<AddonEntry>` (id + count; slot order, no gaps, each addon once per radar; count > 1 only for the
  battery). Persistent form: a plain id string for count 1 (exactly the old `List<ResourceLocation>` format, still read and
  written, so old radars load as count 1) or `{"id": .., "count": n}`. Not stacks: stacks have no value equality and are
  mutable. Ids of removed mods are dropped when read. `RadarItem.addons` still returns the id list; `addonEntries` the counts.
- **Capacity** (`RadarItem.capacity(stack)`) = `energy.capacity + batteries * addons.battery.capacityPerBattery`, where batteries =
  the battery entry's count when it is installed within the tier's slots, enabled and usable. Energy storage cap,
  `RadarItem.energy/setEnergy` clamps, item bar, tooltip, snapshot capacity, LED / energy bar %, `/signalradar charge` use it.
  Removing batteries (menu, command, API) clamps the stored energy to the new capacity: the FE above it is lost.

Upgrade recipe `signalradar:radar_upgrade` (special recipe, needs a grid of 2+ cells): `radar + radar_module_N` gives a radar
at tier N only when the current tier is N-1; every other component (energy, addons, name...) is preserved. Wrong module
tier = no output. The recipe JSON has no ingredients; the serializer does the matching. `PlayerEvent.ItemCraftedEvent`
does not expose the recipe, so `RadarUpgradedEvent` is inferred (grid still holds radar + next-tier module and the result
is exactly one tier higher).

### Default recipes
Vanilla-only shaped recipes (category `equipment`) in `data/signalradar/recipe/default/`, ids `signalradar:default/<name>`. Each
carries the condition `signalradar:default_recipes_enabled` (follows startup config `recipes.enableDefaultRecipes`, default true,
file `config/signalradar-startup.toml`; restart after changing it). Compat addon recipes also need `neoforge:mod_loaded`.
`radar_upgrade` is a special recipe outside `default/` and is never disabled by this switch.

| Id | Pattern | Keys |
|---|---|---|
| `default/radar` | `ILI` `GCG` `IRI` | I iron ingot, L lightning rod, G glass pane, C compass, R redstone |
| `default/radar_module_1` | `CRC` `RPR` `CRC` | C copper ingot, R redstone, P repeater |
| `default/radar_module_2` | `GQG` `QKQ` `GQG` | G gold ingot, Q quartz, K comparator |
| `default/radar_module_3` | `DAD` `AEA` `DAD` | D diamond, A amethyst shard, E ender eye |
| `default/radar_module_4` | `ESE` `SNS` `ESE` | E echo shard, S sculk sensor, N netherite ingot |
| `default/addon_*` | `NRN` `RXR` `NRN` | N iron nugget, R redstone, X = container: chest, ore: iron pickaxe, biosign: egg, structure: map, motion: sculk sensor, manhole: iron trapdoor (needs manholes), loot: ender chest (needs lootr), team: bell (needs ftbteams) |
| `default/addon_battery` | `NRN` `RXR` `NRN` | N copper ingot, R redstone, X redstone block |

Disable: set `recipes.enableDefaultRecipes = false`, or per recipe in KubeJS `event.remove({ id: /^signalradar:default\// })`
(server script, `ServerEvents.recipes`), or override by id in a datapack.

### Tiers
| Tier | Range (narrative / structure), blocks | Addon slots | Max fuzz, blocks |
|---|---|---|---|
| 0 | 256 | 1 | 64 |
| 1 | 512 | 2 | 32 |
| 2 | 1024 | 3 | 16 |
| 3 | 2048 | 4 | 6 |
| 4 | 4096 | 5 | 0 |

All values are in the server config (`scan.rangeByTier`, `addons.slotsByTier`, `scan.fuzzByTier`; exactly 5 values each,
otherwise the defaults are used and a warning is logged; slots are clamped to 1..5 each and honoured by the menu, the scanner,
the command and the battery count; the addon screen keeps its fixed 5-slot layout).

- **Fuzz**: the server offsets each blip by up to `fuzz[tier] * clamp(dist / range, 0, 1)` blocks on x/z (uniform in a disc),
  deterministic per (player, blip id, 10 s time bucket) so blips wobble slowly. Near = sharp. Y is exact.
- **Name reveal**: name shown as `???` until the horizontal distance is <= `reveal_distance` (target JSON, default 128). Addon
  blips use the block/entity name and are always revealed.
- Distance for range, fuzz and reveal is horizontal (the display is top-down).
- Targets with `min_tier` above the radar tier are never sent. Out-of-range targets are sent flagged `outOfRange` (maximum
  fuzz, `???`) so the display can draw rim arrows.

### Addons (server config `addons.<name>.*`)
Radius grows linearly from `radiusMin` (at `minTier`) to `radiusMax` (at tier 4). `radiusMin = radiusMax = 0` means "the
radar's tier range". An addon whose `minTier` is above the radar tier cannot be installed (menu refuses, command reports).
Addon screen (`client/AddonScreen`): background drawn in `renderBg` (called from `renderBackground`, after blur/dim) with
the shader colour reset to white, blend and depth set explicitly, pending GUI batches flushed and the real sheet size (256x256)
passed to `blit` (it used to rely on leaked global render state and the implicit-size overload). Battery slots accept a
stack (slot max = item max stack size for stackable addons, shift-click merges), every other addon slot holds one.
`render` = `super.render` + `renderTooltip`; item tooltips come from vanilla, a refused carried addon shows its reason on the empty slot, a locked slot without a carried item shows the lock hint.

| Addon (config section) | Registered | minTier | Radius min-max | Refresh s | FE/scan | Colour | Detects |
|---|---|---|---|---|---|---|---|
| `container` | always | 0 | 24-48 | 10 | 10 | `#E0A040` | Block entities exposing an item handler or `Container`, plus block tag `signalradar:container_targets` (empty by default). |
| `ore` | always | 1 | 16-32 | 5 | 15 | `#B0B0B0` (fallback) | Blocks in tag `signalradar:ore_targets` (default `#c:ores`). Colour comes from the ore material (built-in table + `colorOverrides`); same-block neighbours within 2 blocks merge into one blip. |
| `biosign` | always | 1 | 32-64 | 1 | 10 | `#4CD964` | Passive animals, villagers, plus entity tag `signalradar:biosign` (empty by default). |
| `structure` | always | 1 | tier range (0-0) | 5 | 10 | `#40C0FF` | Nearest structure per entry of worldgen structure tag `signalradar:scannable_structures` (empty by default), from the structure cache. |
| `motion` | always | 2 | 24-48 | 1 | 20 | `#FF3030` | Hostile mobs (`MobCategory.MONSTER` + entity tag `signalradar:trackable`) **that are moving** (over 0.1 block between samples). Red pulsing blips plus a beep. With a held radar of tier >= `addons.motion.stationaryFromTier` (default 3, 5 = never) still hostiles in range are shown too: category `motion_still`, colour `#8A2020`, steady dim red frame, no pulse, no tick, no beep; moving hits are sorted first when `MAX_HITS` caps the list. |
| `manhole` | `manholes` loaded | 0 | 96-160 | 5 | 5 | `#C8A050` | Manhole Travel nodes the player's network has not opened yet. |
| `loot` | `lootr` loaded | 2 | 48-96 | 10 | 10 | `#B060FF` | Lootr containers and carts the player has not opened yet. |
| `team` | `ftbteams` loaded | 1 | whole dimension (100000) | 1 | 5 | `#40E0D0` | Online FTB Teams members (not yourself) in the same dimension. Normal fuzz applies. |
| `battery` | always | 0 | - | - | 0 | - | Nothing (detector `NONE`). Up to 8 in one slot, +`capacityPerBattery` FE each. Config section has only `enabled`, `minTier`, `capacityPerBattery` (default 10000, 0..100000000). |

Per addon config keys (`signalradar-server.toml`, section `[addons.<name>]`): `enabled` (true), `minTier`, `radiusMin`,
`radiusMax`, `refreshSeconds` (1-3600), `color` (`#RRGGBB`), `energyCost` (0-1000000). `addons.ore.colorOverrides` is a
list of `"material=#RRGGBB"` strings (material = the name after `c:ores/`; unknown materials get a stable hash colour).
Custom addons use their definition values and have no config entries. Only the current dimension is scanned.

### Display (client)
- The radar is active only when held (main hand or offhand); not in hand = off, zero work on server and client.
- Drawn by a BEWLR (`client/RadarItemRenderer`): the baked body model of the tier, then the CRT display as fullbright geometry
  (`RenderType.text` with a white texture; no FBO, shader and Sodium/Iris safe): background, range rings, rotating north marker,
  sweep with fading trail, phosphor-decay blips, rim arrows for far targets, up/down arrows for blips more than 4 blocks above or
  below, dimmed blip with a check mark for found targets, tier pips, energy bar, `NO BATTERY` (empty energy: calm dark screen, battery outline, text) or `NO SIGNAL` (scan cancelled by a script: static noise), status LED
  (green ok, amber below 20 % energy, blinking red no signal).
- Heading-up: the player's facing is up. Sweep period 50 ticks.
- **Blip icons** (`client/RadarIcons`, `icon/IconSpec`): every blip carries an icon spec string (`Blip.icon`) and is drawn as a
  square icon, all the same size, instead of a coloured dot: dark backing, a thin frame in the blip colour (phosphor glow of
  the sweep, pulsing red for motion), the icon inside. Found = dimmed icon + check mark; out-of-range blips keep the rim arrow
  with the icon just inside it; the height arrow sits left of the icon. Specs (server builds them, client resolves them once
  and caches per string; the cache is cleared on resource reload and logout):

  | Spec | Client draws | Used by |
  |---|---|---|
  | `block:<id>` | sprite of the model's NORTH face (else first quad, else particle sprite) as a textured quad, `RenderType.text(block atlas)` | ore, custom `block_tag` (the found block) |
  | `item:<id>` | the item model through `ItemRenderer.renderStatic(GUI)`, scaled to the square and flattened on z (scale 0.02) | container and loot (block item of the found block, fallback chest), structure (`minecraft:map`), narrative targets (default `minecraft:compass`), last death (`minecraft:skeleton_skull`) |
  | `texture:<rl>` | plain PNG quad; a missing `map_icon_<look>.png` falls back to `map_icon.png` of the same folder, else a dot | manholes: `manholes:textures/gui/map_icon_<look>.png` by node look (`home_manhole`, `city`, `grate`, `hatch`, `cave`, `ns:x` looks use their namespace) |
  | `entity:<type id>` | 1. the mob's own face: the head `ModelPart` of its renderer model (`HeadedModel.getHead()`; a `head` part anywhere in a `HierarchicalModel` tree; or a non-static `ModelPart` field named `head` of any `EntityModel`, e.g. MutantsZombies) drawn from the front with the renderer's texture via `RenderType.text` (fullbright), pose neutralised, centred and scaled from the bounds of its visible cubes, flattened on z. 2. a **full-body mini render** (`client/MobBodies`): the cached dummy entity drawn with its renderer's own `render(...)` (any `EntityRenderer`, GeckoLib included), yaw 0 (faces the viewer), tick 0, partial 0, fullbright, scaled to fit the square from its bounding box, flattened on z. 3. the vanilla mob head item (zombie, skeleton, wither skeleton, creeper, piglin, dragon). 4. a dot. **Never the spawn egg.** Each type logs once at INFO `Mob icon <type>: face/body/head/dot (reason)`; a body whose render throws is marked failed (logged once) and draws the head item or an empty frame. All per-type try/catch, cached, cleared on level change and logout | motion, biosign, custom `entity_tag` |
  | `player:<uuid>` | skin face + hat layer (tab list skin, default skin when unknown) | team |
  | empty | the old coloured dot | script blips added by other mods with an empty icon |

  Icon side = screen width / 12 x `iconSize`. At most `maxIcons` nearest blips (horizontal distance) are icons, the rest are dots.
  Layers (model units in front of the screen plane, 0.03 apart): bg .03, disc .06, trail .09, rings .12, sweep .15, blips
  .18 to .265, north marker / you .27, count badge .295, text bg .30, text .34. Blips (`display/BlipLayout`, pure) are
  drawn without flicker: each drawn blip owns a depth slot (0.002 apart, shrinking so all fit in 0.085), slots follow the
  blip id order (never distance, so nothing swaps between frames), and inside a slot the order is rim arrow, dot or backing,
  content (item models are squashed to fit), frame, check mark and height arrow, so everything of a blip stays above the
  previous one. Blips closer than 0.35 icon sizes on screen merge into one (nearest not-found wins the icon, the rest count)
  with a small count badge ("2") on its corner. Icon content is drawn after all other quads (each new texture ends the
  vertex batch), still fullbright (items get a low light only when found). Cosmetic mode draws no blips and so no icons.
- **Hold right-click** raises the device to the face (eased client pose via `applyForgeHandTransform`, not the spyglass
  animation; RAISED_Y -0.28, first-person display translation y +2 px, both set in code / `art/make_tiers.py`) and shows a text line for the blip closest to the crosshair direction: name, distance in metres, compass. An offhand
  radar only raises when the main-hand item has no use action of its own (vanilla priority).
- **Sneak + right-click** opens the addon menu (slots = the tier's slot count, locked slots crossed out).
- **Zoom** (client only, `client/RadarZoom`, steps in `RadarMath.rangeOptions/stepRange/effectiveRange`): while raised, the mouse
  wheel (`InputEvent.MouseScrollingEvent`, cancelled so the hotbar stays put) steps the display range through 4, 8, 16, 32, 64, 128,
  256, 512, 1024, 2048, 4096 m, at most the tier range (`snap.range()`, which is also the default; a non-step cap is offered
  as the last option). Wheel up = larger range (zoom out), wheel down = smaller range (zoom in); the keys keep zoom in = smaller range. Blips beyond the chosen range use the rim arrow like out-of-range ones; the
  range label shows the chosen range. The choice is a client static for the session (0 = follow tier), forgotten when the tier
  cap drops below it. Key mappings "Radar zoom in / out" (unbound, category "Signal Radar") work while a radar is in a hand.
  Quiet UI click on change. The server scan is unchanged.
  **Zoom bands** (`RadarMath.peripheralRange/visibility`, only when a zoom is active, i.e. range < tier range): rim band beyond the
  zoom range for local blips: 4 -> 16, 8 -> 32, 16 -> 32, 32 -> 64, >= 64 -> none (a value between steps uses the step at or
  below; a non-step tier cap has no band). *Local* categories = container, loot, ore, biosign, motion, motion_still and custom addon categories
  (everything not navigation; distances are those of the drawn, glided position, `RadarMath.shownDistance`, never the new snapshot position): distance <= zoom range drawn normally, up to the band on the rim with an arrow, beyond it not drawn
  and not counted in BlipLayout groups/badges. *Navigation* categories = narrative, structure, manhole, team, last_death and
  `script` (KubeJS) keep the old rule: always drawn, on the rim with an arrow beyond the range. No zoom = unchanged.
  **Sounds follow the zoom**: the narrative/motion sweep tick and the motion beep only consider blips within the display range
  (distance <= zoom range); rim, peripheral and hidden blips never sound. Beep rate reference = min(zoom range, snapshot
  motion radius); no in-range motion blip = no beep.
- Live data only in first person for the owner. Third person, other players, item frames, ground: cosmetic sweep with no blips.
  GUI icon: static screen.
- Sounds (`sounds.json`, custom mono 44.1 kHz OGGs synthesized by `tools/make_sounds.py`, never overwrites without `--force`): scan ping (charged scans only), blip tick (narrative category only),
  target found, motion beep (rate rising with proximity).
- Screen and LED rectangles come from `assets/signalradar/radar_screen.json`; resource packs can override it.

### Scan architecture (server-authoritative)
- Only players holding a radar are processed (`ScanHandler` exits early otherwise).
- **Energy**: every `scan.scanRefreshSeconds` (default 5) of holding, the radar pays `ceil((scanCost (default 50 FE) + the
  `energyCost` of every installed, enabled addon) * energyMultiplierByTier[tier])` (default multipliers 1.0, 0.85, 0.7, 0.55, 0.4:
  higher tiers are more efficient; the addon tooltip shows the base cost). Not enough FE = a `NO SIGNAL` snapshot and nothing is charged. The first scan
  happens as soon as the radar is held.
- Snapshots are sent at min(base period, shortest addon refresh); scans between charges are free (cached results). The snapshot
  carries a `charged` flag (only charged scans ping).
- Each detector caches its result per player until its refresh expires (`AddonCache`, keyed by addon, radius and dimension).
- Block detectors (ore, custom `block_tag`, `block` locators) look at loaded chunks only and skip sections with
  `PalettedContainer.maybeHas`. The `block` locators of one player scan share `scan.maxBlockChecksPerScan` (default 200000;
  each section that may contain the block costs 4096); when the budget runs out the nearest block found so far is kept.
- **Structures**: `findNearestMapStructure` is never called from a scan. Lookups go through a queue of at most
  `scan.structureLookupsPerTick` (default 1) per tick; results and misses are cached in world SavedData
  (`StructureCacheData`); a miss is retried at most every 5 minutes; pending entries just do not show yet. The cache is global
  per dimension + locator (nearest to the first requester); the search radius is part of the key. The structure tag is capped by
  `scan.maxScannableStructures` (16); extra entries are ignored with a warning.
- The server applies fuzz and name reveal and sends a compact payload; the client never decides visibility and interpolates
  between snapshots.
- `RadarScanEvent` (NeoForge bus, cancellable) runs before sending; KubeJS `scan` bridges it.
- **Last death**: stored per player on `LivingDeathEvent` (lowest priority, skipped when cancelled); a base-radar blip of category
  `last_death` at every tier, same dimension only, fuzzed like other blips, cleared when the player stands within 8 blocks
  (3D) while carrying a radar.

### Targets (datapack JSON)
Path `data/<namespace>/signalradar/target/<path>.json` (KubeJS: `kubejs/data/...`), reloaded with `/reload`. Not synced to
clients (the snapshot carries name, colour and category). A bad file logs a warning and is skipped. Target ids are
`<namespace>:<path>`.

```json
{
  "name": { "text": "Faint signal" },
  "category": "narrative",
  "min_tier": 1,
  "color": "#7CFC00",
  "reveal_distance": 128,
  "requires_stage": "my_stage",
  "requires_unlock": false,
  "found_radius": 24,
  "hide_when_found": false,
  "locator": { "type": "structure", "structure": "minecraft:village_plains", "search_radius_chunks": 100 }
}
```

| Field | Default | Meaning |
|---|---|---|
| `name` | the path | Any text component (`{"text": ..}`, `{"translate": ..}`, a plain string). |
| `category` | `narrative` | Free string. `narrative` blips tick when the sweep crosses them. |
| `min_tier` | 0 | 0..4. The target is invisible below this tier. |
| `color` | `#7CFC00` | `#RRGGBB` or a number. |
| `reveal_distance` | 128 | Blocks; `???` beyond. |
| `requires_stage` | none | KubeJS stage (scoreboard tag without KubeJS); the target is hidden until the player has it. |
| `requires_unlock` | false | Hidden until `/signalradar unlock` or `SignalRadar.unlock`. |
| `found_radius` | 24 | Horizontal blocks for the found check. |
| `hide_when_found` | false | Hide once found (otherwise drawn dimmed with a check mark). |
| `icon` | `item:minecraft:compass` | Blip icon spec (`block:`, `item:`, `texture:`, `entity:`, `player:` + id; a bare id is an item). An invalid value skips the target with a warning. |
| `locator` | required | See below. |

Locators:

| `type` | Fields | Resolution |
|---|---|---|
| `pos` | `pos` (`[x, y, z]` or `{"x","y","z"}`, y defaults to 64), `dimension` (default `minecraft:overworld`) | Direct. Only shown in that dimension. |
| `structure` | `structure` (id or `#tag`), `search_radius_chunks` (default 100, 1..1000) | Structure cache and lookup queue, per dimension. |
| `entity` | `entity_type` and/or `tag` (scoreboard tag), at least one | Nearest loaded matching entity. |
| `block` | `block` (id or `#tag`), `radius` (default 32, 1..64) | Nearest matching block in loaded chunks, per-player cache, shares the block check budget. |

Examples:
```json
{ "name": {"text": "Drop point"}, "min_tier": 0, "locator": { "type": "pos", "pos": [150, 70, 0] } }
{ "name": {"translate": "target.my_pack.radio"}, "min_tier": 2, "requires_stage": "era_2",
  "locator": { "type": "entity", "tag": "radio_operator" } }
{ "name": {"text": "Beacon"}, "min_tier": 1, "locator": { "type": "block", "block": "minecraft:beacon", "radius": 48 } }
{ "name": {"text": "Old town"}, "min_tier": 3, "locator": { "type": "structure", "structure": "#minecraft:village" } }
```

### Per-player state, found, stages
- Attachment `signalradar:player_data` (codec, `copyOnDeath`), record `PlayerData`: `unlocked`, `found`, `last_death` (pos +
  dimension). Not synced; found flags travel inside snapshots.
- **Found check**: once per second per player, only for players carrying a radar anywhere in the main inventory or offhand whose
  highest tier is >= the target's `min_tier`, and only for targets visible to the player (stage / unlock rules apply). Horizontal
  distance <= `found_radius`. Positions are read cache-only (`Locators.peek`, never starts a lookup): `pos` direct, `structure`
  from the saved cache, `block` from the per-player cache, `entity` within `found_radius` of the player.
- On found (once per player per target): mark found, add stage `signalradar_found_<target path>` (`/` becomes `_`; KubeJS stage
  when KubeJS is loaded, scoreboard tag of the same name otherwise, `StageHelper`), post `RadarTargetFoundEvent` (and KubeJS
  `targetFound`), play `target_found`, action bar `Signal acquired: <name>`. No advancements: FTB Quests owns progression through
  stages.

## Config files
### `signalradar-server.toml` (per world: `<world>/serverconfig/`)
| Key | Default | Notes |
|---|---|---|
| `energy.capacity` | 20000 | Base FE capacity of the radar (plus batteries). |
| `energy.maxReceive` | 2147483647 | FE accepted per insert from chargers, 0..max. Default = no limit: accepted = min(offered, maxReceive, free space). Existing config files keep their old value (was 100) until edited. |
| `scan.scanCost` | 50 | FE per base scan. |
| `scan.scanRefreshSeconds` | 5 | Seconds between base scans. |
| `scan.energyMultiplierByTier` | `[1.0, 0.85, 0.7, 0.55, 0.4]` | 5 values, 0.05..1.0 each (other length = defaults). Per-period charge = `ceil((scanCost + addon costs) * multiplier[tier])`. |
| `scan.rangeByTier` | `[256, 512, 1024, 2048, 4096]` | |
| `scan.fuzzByTier` | `[64, 32, 16, 6, 0]` | |
| `scan.structureLookupsPerTick` | 1 | 1..64. |
| `scan.maxScannableStructures` | 16 | 1..256. |
| `scan.maxBlockChecksPerScan` | 200000 | 4096..max. |
| `addons.slotsByTier` | `[1, 2, 3, 4, 5]` | Each clamped to 1..5. |
| `addons.battery.capacityPerBattery` | 10000 | FE per installed battery (0..100000000). |
| `addons.<container/ore/biosign/structure/motion/manhole/loot/team>.{enabled,minTier,radiusMin,radiusMax,refreshSeconds,color,energyCost}` | see the addon table | |
| `addons.motion.stationaryFromTier` | 3 | Radar tier (0-5) from which the motion tracker also shows stationary hostiles (`motion_still`); 5 = never. Read at scan time. |
| `addons.container.includeLootrContainers` | `true` | When false the container addon skips Lootr block entities (use it with the Loot addon so chests are not listed twice). |
| `addons.ore.colorOverrides` | `[]` | `"iron=#D8AF93"` style entries. |

### `config/signalradar-startup.toml` (STARTUP, loaded in the mod constructor)
| Key | Default | Notes |
|---|---|---|
| `recipes.enableDefaultRecipes` | true | Loads `signalradar:default/*` recipes via the `signalradar:default_recipes_enabled` condition; restart after a change. |

### `config/signalradar-client.toml`
| Key | Default | Notes |
|---|---|---|
| `motionBeep` | true | Motion tracker beep. |
| `screenBrightness` | 1.0 | 0.2..1.0. |
| `showHeightArrows` | true | Up/down arrows for blips more than 4 blocks above/below. |
| `iconSize` | 1.0 | 0.5..2.0. Icon size factor (1.0 = about 12 icons across the screen). |
| `maxIcons` | 48 | 0..256. Nearest blips drawn as icons, the rest as dots (0 = dots only). |

## Commands (op level 2)
- `/signalradar settier <player> <0-4>`
- `/signalradar charge <player>` (fill the held radar)
- `/signalradar addon <player> add|remove <addon id>` (same rules as the menu; fires `addonChanged`)
- `/signalradar unlock|lock <player> <target id>`
- `/signalradar resetfound <player> <target id|all>` (clears the found flag and the stage)
- `/signalradar targets [player]` (loaded targets; with a player: tier, unlock, stage, found, known position)
- `/signalradar clearcache` (structure cache and per-player caches)

## KubeJS API (KubeJS 2101.7.x, optional; examples in `src/test_datapack/kubejs/`)
Plugin found through `kubejs.plugins.txt`; all classes in `compat/kubejs/`. Ids without a namespace get `kubejs:`; a leading `#`
is ignored. **Bad ids never throw into scripts**: binding methods return false / null / do nothing (and log a warning); non-radar
stacks, empty stacks and null are ignored (`getTier` and `getEnergy` return 0). The startup builder is stricter: invalid
definitions are reported to the startup console and skipped, and one bad addon does not stop the others.

**Startup** `SignalRadarEvents.registerAddons(e => ...)`:
```js
SignalRadarEvents.registerAddons(e => {
  e.create('my_pack:addon_oil', 'block_tag')   // detector: container | block_tag | entity_tag | structure_tag
    .tag('#c:ores/oil').minTier(3).radius(16, 48).refresh(5)
    .color('#222222').energy(10).category('Oil').requiredMod('somemod')
    .icon('minecraft:lava_bucket')   // optional blip icon, see "Blip icons"; bare id = item
})
```
Custom addon blip icons default by detector: `block_tag` the block face, `entity_tag` the mob face (or body / head item), `container`
the block item, `structure_tag` a map; `.icon('...')` replaces that for every blip of the addon (a bad spec is reported at
startup and skips that addon). Custom addons without a model use the generic tinted model `signalradar:item/addon_custom` (tint = `.color`), without a lang entry
the name "Radar Addon (Oil)". Ship `assets/<ns>/models/item/<path>.json` and lang entries (`kubejs/assets`) to override. A restart is
needed after editing (addons are items).

**Server events** (`SignalRadarEvents.*`):
- `scan`: `player, radar, tier, range, targets, targetIds`, `removeTarget(id)`, `removeCategory(cat)`, `removeIf(t => ..)`,
  `addTarget(name, color, x, y, z)`, `cancel()` (= NO SIGNAL for this scan; the energy is already paid). Script blips have id
  `script/<name>`, category `script`, no fuzz.
- `targetFound`: `player, targetId, target, targetName`.
- `upgraded`: `player, radar, oldTier, newTier`.
- `addonChanged`: `player, radar, slot, oldAddon, newAddon` (ids, null = empty slot).

**Binding `SignalRadar`** (server scripts): `isRadar(stack)`, `getTier/setTier(stack, n)`, `getEnergy/setEnergy(stack, fe)`,
`getAddons(stack)` (list of ids), `hasAddon(stack, id)`, `unlock/lock/isUnlocked/isFound(player, id)`,
`resetFound(player, id|'all')`, `getTargetPos(level, id)` (Vec3 or null; `pos`, cached `structure`, loaded `entity`), `targets()`.

A clean template with every event and binding: `src/test_datapack/kubejs/server_scripts/signalradar_template.js` (switched off with
`ENABLED = false`). `signalradar_example.js` and `signalradar_example_addons.js` are test fixtures.
Rhino: use `let` (not `const`) inside loops, compare levels by dimension not `.equals`, read Java lists with `size()`/`get(i)`.

## API for other mods (NeoForge)
Package `it.ratlab.signalradar.api` (server side, all static, none starts a structure search or block scan):
- `SignalRadarAPI` (also `getCapacity(stack)`, `getAddonCount(stack, id)`): `targetIds()`, `getTarget(id)`, `getTargetPos(level|player, id)`, `unlock/lock/isUnlocked/isFound/resetFound/
  resetAllFound(player ...)`, `getLastDeath(player)`, `isRadar/getTier/setTier/getEnergy/setEnergy/getAddons/setAddons/hasAddon(stack ...)`.
- Events on `NeoForge.EVENT_BUS`: `RadarScanEvent` (cancellable; mutable blip list, `addTarget`, `removeTarget`, `removeCategory`,
  `removeTargets(predicate)`), `RadarTargetFoundEvent`, `RadarUpgradedEvent`, `RadarAddonChangedEvent`.
- Custom addons: `AddonRegistry.registerCustom(AddonDefinition)` (builder: `AddonDefinition.builder(id, detector)`), allowed until
  our item `RegisterEvent` starts; later calls log an error and return false. `AddonRegistry.addProvider(Runnable)` runs
  inside that event just before the registry freezes (KubeJS uses it). Public detector types: container, block_tag,
  entity_tag, structure_tag.

## Compat
| Mod | What | Class |
|---|---|---|
| Manhole Travel 1.7+ | `addon_manhole`: registry nodes (works in unloaded chunks), same dimension, horizontal radius, no home manholes, not `ManholesAPI.isOpen(player, id)`; name = node display name. | `compat/manholes/ManholeDetector` |
| Lootr | `addon_loot`: loaded chunks, `ILootrBlockEntity` / `ILootrEntity` with `!hasOpened(player)` (team aware; `hasLootAvailable` avoided, it builds inventories). | `compat/lootr/LootrDetector` |
| FTB Teams | `addon_team`: `getTeamForPlayer(p).getOnlineMembers()` minus self, same dimension. | `compat/ftbteams/TeamDetector` |
| KubeJS | events, binding, stages, startup addons. | `compat/kubejs/*` |
| JEI | Info pages for the radar, the four modules and every active addon (text built from the addon tooltip). Client only, optional. | `compat/jei/SignalRadarJeiPlugin` (`@JeiPlugin`) |

Detectors are dispatched from `Detectors.run` behind `AddonRegistry.modPresent`; a `LinkageError` is caught and logged.
Removing an optional mod from an existing world drops its addons from radar slots.

## Networking
Payload protocol version `5` (`RadarNetworking.PROTOCOL`). One payload, server to client: `SnapshotPayload` (blips with id,
category, colour, fuzzed position, name or `???`, flags, icon spec (max 256 chars); plus `range`, `refreshSeconds`, `noSignal`, `charged`), at most `MAX_BLIPS`
blips (decode throws above). History: v2 range + refresh, v3 `charged`, v4 motion radius, v5 blip icon. The addon menu is a vanilla container
menu (`signalradar:addons`).

## Assets pipeline
- `art/radar.bbmodel` (Blockbench source), `art/radar.json` (Java block/item export, tier 0), `art/radar.png`.
- `python art/make_tiers.py` builds `models/item/radar.json` (`builtin/entity` parent + display transforms) and
  `radar_body_t0..t4.json` (mast grows one segment per tier). Bodies are standalone models registered with
  `ModelEvent.RegisterAdditional`. Keep the element names `antenna`, `antenna_tip`, `screen`, `led` when re-exporting.
  Details: `art/README.md`.
- `assets/signalradar/radar_screen.json`: `screen` and `led` boxes in model units; the display plane is `z = screen.to.z` facing +z.
- **All textures are placeholders** (radar, modules 1-4, the eight addon icons, `addon_custom`, `gui/addon_slots.png`); the mod
  draws nothing itself besides `textures/misc/white.png`. `tools/make_placeholders.py` generates missing ones and never
  overwrites an existing PNG.
- English only (`lang/en_us.json`).

## Tests and how to run them
Use `JAVA_HOME="/c/Program Files/Java/jdk-25"` on the dev machine.

| Command | Needs | Result (1.0.0) |
|---|---|---|
| `./gradlew test` | nothing | JUnit tests (pure logic: display, addon math, node filter, ore colours, scan schedule, icon specs and head mapping) |
| `./gradlew runGameTestServer` | nothing | game tests (`BatteryGameTests`: capacity math, stacking in the menu, unlimited maxReceive, old component format, slot config); optional-mod checks pass trivially without their mod |
| `./gradlew runGameTestServerKubeJS` | `tools/prepare-kubejs-run.sh` (KubeJS + Rhino jars from the Gradle cache, example and template scripts copied to `run-kubejs`) | same game tests, the KubeJS ones run for real |
| `./gradlew runGameTestServerCompat` | `tools/prepare-compat-run.sh` (Manhole Travel, Lootr, FTB Teams/Library, Architectury jars from the pack's `mods/`; it also writes `eula.txt` into the game-test-only directory `run-compat`) | same game tests, real compat detectors |
| `./gradlew runClient` / `runClientKubeJS` | | dev client (the second with KubeJS and the example scripts) |

Game tests register only with `-Dsignalradar.gametests=true` (set by the gameTestServer run configs); they ship inside the jar
but stay inert. `src/test_datapack/` (not in the jar): five `pos` targets, one per tier (`signalradar_test:tier0..4`), plus the
KubeJS scripts; see its README. `libs/` is gitignored: `manholes-1.7.0.jar`, `lootr-neoforge-1.21.1-1.11.38.127.jar` and
`jei-1.21.1-neoforge-19.57.0.449.jar` must be copied there from the pack before building a fresh checkout.

## Known limitations
- Not play-tested in a live client yet (display geometry, raise pose, sounds, menu and JEI pages are verified by code and headless
  tests only; the client boot check only proves the mod loads).
- Blip icons are verified by code, headless tests and a client boot only: size, readability and the look of flattened item models (lighting of block items) still need an eyeball in game.
- Structure lookups can take a while to appear after the first scan (queue + cache); misses retry every 5 minutes.
- `RadarUpgradedEvent` is inferred from the crafted result (no recipe in `ItemCraftedEvent`).
- The team addon applies normal fuzz (it does not show exact teammate positions).
- Offhand raise-to-face does not work while the main hand holds an item with its own use action (shield, bow...).
- Blips of other dimensions are never shown (`pos` locators carry a dimension, everything else scans the current one).
- The found check counts the highest-tier radar carried; a radar in an ender chest or backpack does not count.
- English only; default recipes are vanilla-item placeholders (balance is the pack's call); all art is placeholder.
- The dedicated `runServer` start was not exercised (needs an accepted EULA); class loading on a server is covered by the three
  `gameTestServer` runs.

## Decisions (judgement calls)
1. Datapack targets are not synced to the client; snapshots carry everything the display needs.
2. Range, fuzz and reveal use horizontal distance; the found radius is horizontal too (structure positions have no reliable Y).
3. The `addons` component is an id list, not stacks.
4. Addon items are registered in our own `RegisterEvent` (not a `DeferredRegister`) so any namespace works; custom addons are
   accepted until that event starts.
5. Only built-in addons get config entries; static defaults live in the definition, config is read at use time.
6. Energy is charged once per base period, snapshots may be more frequent (free between charges).
7. Snapshot stale check = 2 x refresh (10 s when unknown); heading-up display; the raise is an eased client value (5 ticks).
8. The display uses `RenderType.text` with a white texture (true fullbright, shader friendly) instead of an entity render type.
9. The raise pose goes through `applyForgeHandTransform`, not `UseAnim.SPYGLASS` (that hides the item and zooms the FOV).
10. The found check reads positions from caches only and never triggers lookups. Stages through KubeJS when present, scoreboard tags otherwise.
11. Bad ids in KubeJS calls log and no-op instead of throwing; the startup builder reports errors per addon.
12. Opening (or closing) a Lootr container (`PlayerContainerEvent`, menu with an `ILootrInventory` slot; `compat/lootr/LootrEvents`) drops that player's Loot cache entry and pulls the next free snapshot within 5 ticks (`ScanSchedule.pullForward`, only in a paid period, never an extra charge).
13. The manhole addon reads the node registry (no block scan) and hides nodes the network opened; the Lootr addon avoids loot generation.
14. JEI support is an isolated `@JeiPlugin` class reusing the addon tooltip; JEI is never a hard dependency.
15. Off-the-shelf radar mods (sonar style, satellites) were rejected in planning in favour of this custom mod.

Hand transforms note: vanilla `ItemTransform.apply(leftHand)` mirrors the left hand itself (negates translation x, rotation y/z), so
`firstperson_lefthand` and `thirdperson_lefthand` hold the same numbers as the right entries (enforced by `art/make_tiers.py`,
math in `display/HandMath`). A pre-mirrored left entry is mirrored twice and the screen turns away.
