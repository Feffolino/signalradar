# Changelog

## Unreleased

- Changed: the Loot addon is now the Lootr addon: item `signalradar:addon_loot` became `signalradar:addon_lootr` (name "Lootr Addon"),
  category `loot` became `lootr`, config section `[addons.loot]` became `[addons.lootr]` (old values are not migrated: copy them by
  hand, otherwise the defaults apply). Saved radars and stacks keep working: the old id is an item registry alias and the addons
  component maps `addon_loot` to `addon_lootr` when it is read. The default recipe is now `default/addon_lootr`. Add
  `textures/item/addon_lootr.png` for the icon.
- New: custom addon textures from KubeJS. Put a PNG at `kubejs/assets/<ns>/textures/item/<addon path>.png` (default) or choose a
  file with the new builder option `.texture('ns:item/xxx')` (also `AddonDefinition.Builder.texture`). Addons without an own
  `models/item` json get an `item/generated` model of that texture through an in-memory client pack (lowest priority, so a real
  model still overrides it). When the PNG does not exist the addon falls back to the generic tinted model as before (never
  purple/black). The client logs `Custom addon <id>: texture <rl>` / `generic (texture missing)` once per addon.
- Fixed: an offhand radar stayed active while the main hand used a two-handed item. Now it is off (no scan, no FE drain, no snapshots, sounds or beeps; dark screen, LED off) while the main hand uses a bow, crossbow, spear/trident, spyglass, brush or goat horn, holds a charged crossbow, or holds an item of the new tag `signalradar:two_handed` (empty by default: packs add guns etc.; optional entries are fine). On the client a two-handed arm pose of the main hand item also counts. The main-hand radar is unaffected and the offhand radar resumes without an extra paid scan.
- Fixed: blips near the zoom range edge vanished and came back about 1 s later. Zoom visibility (rim band / hidden), merging and
  the icon limit used the blip's new snapshot position while the blip was still drawn gliding (1 s) from its old one, so a blip
  whose new position was across a zoom boundary disappeared at once and reappeared when the glide ended. The layout now uses
  the drawn (glided) position, like the sounds already did.
- Mob icons never use spawn eggs any more. Order: the mob's face (head model part; now also found when nested in the model tree
  or kept in a plain `head` field of a custom `EntityModel`, e.g. MutantsZombies), else a full-body mini render of the mob with its own
  renderer (front facing, frozen, fullbright, flattened; works for GeckoLib mobs such as Zombie Island), else the vanilla mob head
  item, else a dot. The client log says once per type which path is used: `Mob icon <type>: face|body|head|dot (reason)`.
- `addons.slotsByTier` values are clamped to 1..5 (0 is no longer accepted; the addon screen keeps its 5-slot layout).
- Charging limit removed: `energy.maxReceive` now defaults to 2147483647 (no limit; the key stays, 0..max). A charger fills
  the radar as fast as it pushes: accepted = min(offered, maxReceive, free space). Existing config files keep their old value
  (100) until edited.
- New **Battery Addon** (`signalradar:addon_battery`, stacks to 8): a whole stack fits in ONE addon slot and each battery adds
  `addons.battery.capacityPerBattery` FE (server config, default 10000) to the radar capacity
  (`capacity = energy.capacity + batteries * capacityPerBattery`). Energy storage, item bar, tooltip, snapshot, LED and energy bar use
  the per-radar capacity. Removing batteries clamps the stored energy to the new capacity (the extra FE is lost). Detects nothing,
  costs no energy, every tier. Default recipe: copper ingots, redstone and a redstone block. Only the battery stacks; every
  other addon still holds one item per slot.
- The `signalradar:addons` component now stores counts: a plain id (count 1, the old format, still read and written) or
  `{"id": .., "count": n}`. Old radars load unchanged. API: `SignalRadarAPI.getCapacity/getAddonCount`, KubeJS `SignalRadar.getCapacity`.
- Fixed: the addon screen background could show broken or missing. It now resets the shader colour, blend and depth state
  before drawing and passes the real sheet size (256x256) to `blit`.
- Zoom: the mouse wheel direction is inverted (wheel up = larger range, wheel down = smaller range). The zoom keys are unchanged.
- Motion tracker: from radar tier 3 it also shows stationary hostiles (new category `motion_still`: entity face icon in a steady dim red
  frame, no pulse, no beep; moving ones still pulse and beep). Config `addons.motion.stationaryFromTier` (0-5, default 3, 5 = never).
  `Hit` gained an optional per-hit category override. Addon tooltip updated.

- Zoom-aware display and sounds: with a zoom active, local blips (containers, lootr, ore, biosigns, motion, custom addons) show on
  the rim only up to a peripheral band (4 m -> 16, 8/16 m -> 32, 32 m -> 64, none from 64 m) and vanish beyond it; navigation
  blips (narrative, structure, manhole, team, last death, script) still always show. The narrative/motion tick and the motion
  beep now ignore everything outside the displayed range, and the beep rate is relative to min(zoom range, motion radius).
- Mob icons (motion, biosign, entity targets) show the mob's face: the head model of the mob's own renderer with its texture,
  drawn flat from the front (villager nose, pig snout, ...), instead of the spawn egg. Mobs without a head model fall back to the
  vanilla mob head item, then the spawn egg. Client only; uses an access transformer on `ModelPart.cubes/children`.
- Fixed: a Lootr chest you just opened stayed on the radar until the Loot addon refreshed (10 s). Opening or closing a Lootr
  container now drops your cached Lootr result and sends the next snapshot within a fraction of a second, at no extra energy.
- New server config `addons.container.includeLootrContainers` (default true): when false the container addon skips Lootr
  containers, so with the Lootr addon a chest is not listed twice.
- Higher tiers are more energy-efficient: new server config `scan.energyMultiplierByTier` (default 1.0, 0.85, 0.7, 0.55, 0.4;
  0.05..1.0). The FE charged per scan period is `ceil((scanCost + addon costs) * multiplier)`.
- The screen shows `NO BATTERY` (calm dark screen with a battery outline, red blinking LED) when the radar has no energy;
  `NO SIGNAL` with static stays for a scan cancelled by a script.
- Zoom: while the radar is raised (holding right-click) the mouse wheel changes the display range in steps of 4, 8, 16, 32, 64,
  ... 4096 m, capped at the tier range (default = tier range, remembered for the session). Blips beyond the chosen range go to
  the rim with an arrow; the range label follows. Client only, the server still scans the full range. Optional unbound keys
  "Radar zoom in / out" (category "Signal Radar") work while holding the radar. The hotbar does not scroll while raised.
- Fixed: the addon screen showed no item tooltips on hover (refusal tooltips for locked or invalid slots still work).
- Fixed: icons and height arrows flickered when two blips overlapped (z-fighting). Blips now have a stable draw order and
  their own depth slots; blips almost on the same spot merge into one icon with a count badge.
- Fixed: the radar in the off hand faced away from the player (left-hand display transform was mirrored twice).
- The device sits about 2 model px higher in the hands and slightly higher when raised.
- Real custom sounds (scan ping, blip, target found, motion beep) replace the vanilla note block placeholders; generated by
  `tools/make_sounds.py`.

- Blips are drawn as clear, same-size icons instead of coloured dots: the block face for ores and custom block addons,
  item icons for containers, lootr, structures, narrative targets (compass) and the last death (skull), the Manhole Travel map
  icon of each manhole look, mob heads or spawn eggs for motion and biosign, the skin face of team members. Thin frame in the
  blip colour (motion still pulses), found icons dimmed with a check mark, rim arrows kept.
- Target JSON: optional `"icon"` (`block:`, `item:`, `texture:`, `entity:`, `player:` or a bare item id). KubeJS addon builder:
  optional `.icon(...)`. Client config: `iconSize` (0.5..2.0) and `maxIcons` (default 48, the nearest become icons).
- Network protocol version 5 (snapshot blips carry an icon spec); client and server must match.

## 1.0.0

First release.

- Handheld radar `signalradar:radar` powered by Forge Energy (chargeable by any FE item charger), energy bar and tooltip.
- Display drawn on the item: sweep, range rings, north marker, blips, rim arrows, height arrows, found marks, tier pips, energy
  bar, status LED and `NO SIGNAL` static. Hold right-click to raise the device and read name, distance and compass of the blip
  you are looking at. Cosmetic sweep for third person and other players.
- Five tiers with upgrade modules 1 to 4 (range 256 to 4096, less fuzz, 1 to 5 addon slots). Upgrading keeps energy and addons.
- Addon menu (sneak + right-click) and eight built-in addons: container, ore (coloured by material), biosign, structure,
  motion tracker (moving hostiles only, beep), manhole (Manhole Travel), loot (Lootr), team (FTB Teams).
- Last death marker on every radar.
- Datapack targets with `pos`, `structure`, `entity` and `block` locators, minimum tier, stage and unlock requirements,
  reveal distance, found radius; found flow with stage `signalradar_found_<target>`, sound and action bar.
- Server-authoritative scans: name reveal, deterministic fuzz, structure lookup queue with cache, block scan budget,
  per-addon refresh and FE cost.
- Server and client config, `/signalradar` commands (settier, charge, addon, unlock, lock, resetfound, targets, clearcache).
- KubeJS: events `scan`, `targetFound`, `upgraded`, `addonChanged`, startup `registerAddons` for custom addons, `SignalRadar`
  binding with lenient id handling; clean script template.
- NeoForge API: `SignalRadarAPI`, scan / found / upgrade / addon-changed events, addon registry hooks.
- Default vanilla crafting recipes (`signalradar:default/*`) for the radar, modules 1 to 4 and all addons, behind the startup config
  `recipes.enableDefaultRecipes` (`signalradar-startup.toml`) and the condition `signalradar:default_recipes_enabled`.
- JEI info pages for the radar, the modules and every addon.
- English translation.
