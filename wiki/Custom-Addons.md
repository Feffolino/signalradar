# Custom addons with KubeJS

Signal Radar ships built-in addons (container, ore, biosign, structure, motion tracker, battery, plus Manhole Travel / Lootr /
FTB Teams ones). With [KubeJS](https://kubejs.com) (2101.7+) a pack can register **new addon items** that reuse one of the
built-in detectors. Addons are items, so they are registered in a **startup script** and need a **game restart** after edits.

## 1. Register the addon

`kubejs/startup_scripts/radar_addons.js`:

```js
SignalRadarEvents.registerAddons(event => {
  event.create('mypack:addon_oil', 'block_tag')  // id, detector
    .tag('#c:ores/oil')        // what to look for (required for the *_tag detectors)
    .minTier(3)                // radar tier needed to install it (0-4)
    .radius(16, 48)            // radius at minTier .. radius at tier 4 (blocks)
    .refresh(5)                // seconds between detector runs
    .color('#222222')          // blip frame colour (also tints the generic icon)
    .energy(10)                // extra FE per scan while installed
    .category('Oil')           // category name shown on the radar
    .icon('minecraft:lava_bucket')  // optional blip icon (see below)
    .texture('mypack:item/oil')     // optional item texture (see step 2)
    .requiredMod('somemod')         // optional: only registered when that mod is loaded
})
```

| Detector | Finds | Needs |
|---|---|---|
| `block_tag` | blocks in a block tag, loaded chunks only, one blip per vein | `.tag('#ns:tag')` |
| `entity_tag` | entities in an entity-type tag | `.tag('#ns:tag')` |
| `container` | block entities with an inventory | nothing |
| `structure_tag` | nearest structure of each entry of a structure tag (cached) | `.tag('#ns:tag')` |

Ids without a namespace get `kubejs:`. Colours accept `'#RRGGBB'` or `0xRRGGBB`. A bad value is reported in the startup log and
that addon is skipped; the others still register.

**Blip icon** (`.icon`, optional): `item:ns:id` (or a bare item id), `block:ns:id`, `entity:ns:id`, `texture:ns:textures/....png`.
Without it the icon follows the detector: block face, mob face, the container's block item, a map for structures.

## 2. Item texture

KubeJS serves `kubejs/assets/` as a resource pack. Put a 16x16 PNG at:

```
kubejs/assets/<ns>/textures/item/<addon path>.png     e.g. kubejs/assets/mypack/textures/item/addon_oil.png
```

or point `.texture('ns:item/xxx')` at `kubejs/assets/ns/textures/item/xxx.png`. How the item looks, in order:

1. your own model `kubejs/assets/<ns>/models/item/<path>.json`, if present;
2. otherwise the PNG above (an `item/generated` model is made for you);
3. otherwise a generic radar-addon icon tinted with `.color()` — never the purple/black missing texture.

The client log says which one each addon uses: `Custom addon <id>: texture <rl>` or `generic (texture missing)`.
Changing a PNG only needs F3+T; a new or changed `.texture()` needs a restart.

## 3. Name and recipe

Name: `kubejs/assets/<ns>/lang/en_us.json`

```json
{ "item.mypack.addon_oil": "Oil Scanner Addon" }
```

Without it the item is called "Radar Addon (<category>)".

Recipe (custom addons ship none): `kubejs/server_scripts/radar_recipes.js`

```js
ServerEvents.recipes(event => {
  event.shaped('mypack:addon_oil', ['NRN', 'RXR', 'NRN'], {
    N: 'minecraft:iron_nugget', R: 'minecraft:redstone', X: 'minecraft:bucket'
  })
})
```

## 4. Use it

Sneak + right-click the radar to open the addon slots and drop the addon in (the radar must be at least `minTier`). Its blips
show up on the display with the addon colour; every scan costs `energy` FE more (less on higher radar tiers).

## Tips

- Keep `radius` small for `block_tag` addons on common blocks: block scans have a per-scan work budget
  (`scan.maxBlockChecksPerScan`).
- React to installs with the server event `SignalRadarEvents.addonChanged(e => ...)` and query radars with the
  `SignalRadar.hasAddon(stack, id)` binding — see `SUMMARY.md`, section KubeJS.
- A full working example: `src/test_datapack/kubejs/startup_scripts/signalradar_example_addons.js`.
