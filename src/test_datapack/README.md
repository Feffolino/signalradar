# Signal Radar test datapack

Not part of the mod jar. Five `pos` targets in the overworld, one per tier (`min_tier` 0..4):

| Target | min_tier | Position |
|---|---|---|
| `signalradar_test:tier0` | 0 | 150 70 0 |
| `signalradar_test:tier1` | 1 | 0 70 400 |
| `signalradar_test:tier2` | 2 | -800 70 0 |
| `signalradar_test:tier3` | 3 | 0 70 -1500 |
| `signalradar_test:tier4` | 4 | 3000 70 3000 |

Install: copy the folder `signalradar_test` into `<world>/datapacks/`, then `/reload` (or restart), and `/datapack enable "file/signalradar_test"` if it is not on already.
Check with `/signalradar targets`, then hold a radar (`/give @s signalradar:radar`, `/signalradar charge @s`, `/signalradar settier @s 2`).
Tier 0 already sees `tier0` (150 m away, within its 256 m range); `tier4` is beyond tier 0 range but hidden anyway until the radar is tier 4.

## Cost of `block` locators
A `block` locator searches the loaded chunks around the player (sphere, `radius` clamped to 1..64) on every scan of a
player holding a radar (cached for one scan period). Chunk sections that cannot contain the block (palette check) are
skipped for free; every other section costs 4096 block checks. All block targets of one player scan share the server
config budget `maxBlockChecksPerScan` (default 200000, about 48 sections); when it runs out the search stops and keeps
the nearest block found so far. Worst case (radius 64, a common block such as stone): about 700 sections, so the budget
decides. Prefer rare blocks, small radii and few block targets; use `pos` or `structure` locators where possible.

## KubeJS examples (`kubejs/`)
Not a datapack: copy `kubejs/startup_scripts/*.js` and `kubejs/server_scripts/*.js` into the instance's `kubejs/` folder
(`tools/prepare-kubejs-run.sh` does this for `run-kubejs`). The startup script registers three custom addons
(`signalradar_example:addon_oil`, `addon_undead`, `addon_optional` (needs a missing mod, so no item)); the server
script uses every `SignalRadarEvents` server event and the `SignalRadar` binding. Its branches are switched on by the
scoreboard tags `sr_example_edit`, `sr_example_cancel` and `sr_example_binding` (the game tests use them), and it tags
players with `sr_js_*` results. `./gradlew runGameTestServerKubeJS` runs the KubeJS game tests; `./gradlew runClientKubeJS`
starts a dev client with KubeJS for in-game checks.
