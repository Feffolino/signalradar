# Changelog

## 1.0.0-1.20.1 - Port to Forge 1.20.1
- Ported from NeoForge 1.21.1 to Minecraft Forge 1.20.1 (Forge 47.4.23, Java 17).
- Replaced 1.21 Data Components on the radar item with robust NBT storage helper utilities (`getOrCreateTag`).
- Replaced 1.21 item capabilities with Forge 1.20.1 `ICapabilityProvider` (`ForgeCapabilities.ENERGY`).
- Replaced `CustomPacketPayload` and `StreamCodec` with Forge `SimpleChannel` and `FriendlyByteBuf`.
- Replaced `ModConfig.Type.STARTUP` with early-read TOML / common config for default recipes toggle.
- Replaced recipe Codecs with Forge `RecipeSerializer` (`fromJson`, `fromNetwork`, `toNetwork`) and `IConditionSerializer` / `CraftingHelper.register`.
- Migrated data directories and tags from 1.21 singular forms to 1.20.1 plural forms (`recipes/`, `loot_tables/`, `tags/items`, `tags/blocks`, `tags/entity_types`).
- Updated optional dependencies for 1.20.1:
  - KubeJS 6 (`2001.6.5-build.26`) and Rhino (`2001.2.3-build.10`)
  - FTB Teams (`2001.3.2`), FTB Library (`2001.2.13`), Architectury (`9.2.14`)
  - Lootr (`0.7.35.94`)
  - JEI (`15.59.0.212`)
  - Manhole Travel (`1.7.3-1.20.1`)
- Preserved all display math, BEWLR rendering, access transformers for entity model parts, 5 radar tiers, 8 built-in addons plus battery, narrative targets, structure caching, fuzz and sounds.

## 1.0.0
First release (NeoForge 1.21.1).
- Handheld radar `signalradar:radar` powered by Forge Energy (chargeable by any FE item charger), energy bar and tooltip.
- Display drawn on the item: sweep, range rings, north marker, blips, rim arrows, height arrows, found marks, tier pips, energy
  bar, status LED and `NO SIGNAL` static. Hold right-click to raise the device and read name, distance and compass of the blip
  you are looking at. Cosmetic sweep for third person and other players.
- Five tiers with upgrade modules 1 to 4 (range 256 to 4096, less fuzz, 1 to 5 addon slots). Upgrading keeps energy and addons.
- Addon menu (sneak + right-click) and eight built-in addons: container, ore (coloured by material), biosign, structure,
  motion tracker (moving hostiles only, beep), manhole (Manhole Travel), loot (Lootr), team (FTB Teams).
- Battery addon: stackable up to 8 per slot, expands capacity by configurable FE per battery.
- Last death marker on every radar.
- Datapack targets with `pos`, `structure`, `entity` and `block` locators, minimum tier, stage and unlock requirements,
  reveal distance, found radius; found flow with stage `signalradar_found_<target>`, sound and action bar.
- Server-authoritative scans: name reveal, deterministic fuzz, structure lookup queue with cache, block scan budget,
  per-addon refresh and FE cost.
- Server and client config, `/signalradar` commands (settier, charge, addon, unlock, lock, resetfound, targets, clearcache).
- KubeJS: events `scan`, `targetFound`, `upgraded`, `addonChanged`, startup `registerAddons` for custom addons, `SignalRadar`
  binding with lenient id handling; clean script template.
- API: `SignalRadarAPI`, scan / found / upgrade / addon-changed events, addon registry hooks.
- Default vanilla crafting recipes (`signalradar:default/*`) for the radar, modules 1 to 4 and all addons.
- JEI info pages for the radar, the modules and every addon.
- English translation.
