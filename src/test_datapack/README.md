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
