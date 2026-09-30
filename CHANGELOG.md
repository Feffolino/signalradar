# Changelog

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
- JEI info pages for the radar, the modules and every addon.
- English translation.
