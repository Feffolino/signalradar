# Radar model pipeline

| File | What |
|---|---|
| `radar.bbmodel` | Blockbench source (edit this). |
| `radar.json` | Blockbench "Export Java Block/Item model" of the tier 0 radar. Input of the script. |
| `radar.png` | Texture (128x128, 2 px per model unit). Shipped as `textures/item/radar.png`. |
| `make_tiers.py` | Builds the shipped models from `radar.json`. |
| `radar_item.json`, `radar_t1..t4.json` | Legacy outputs of the phase 1-2 script (item overrides on `signalradar:tier`). No longer used by the mod; kept for reference. |

## Steps after editing the model

1. Blockbench: File > Export > Export Block/Item Model, overwrite `art/radar.json`. Keep the element names
   `antenna`, `antenna_tip` (the script rebuilds the mast per tier), `screen` and `led`.
2. `python art/make_tiers.py` writes into `src/main/resources/assets/signalradar/models/item/`:
   - `radar.json`: `builtin/entity` parent + the `display` transforms + particle texture. The item is drawn by the
     BEWLR `client/RadarItemRenderer`.
   - `radar_body_t0..t4.json`: geometry only, one per tier (mast grows one segment per tier). Registered as standalone
     models (`ModelEvent.RegisterAdditional`, `ModelResourceLocation.standalone(signalradar:item/radar_body_tN)`); the
     renderer picks one by `RadarItem.tier(stack)`.
3. If the screen or LED moved, update `src/main/resources/assets/signalradar/radar_screen.json` with the new `from`/`to`
   of the `screen` and `led` elements (model units). The display is drawn on the plane `z = screen.to.z`, facing +z
   (the screen element's south face); the LED's south and top faces are painted. The file is reloaded with F3+T.
4. Display transforms: tune in Blockbench's Display tab, re-export, rerun the script. The raise-to-face pose is code
   (`client/RadarClientExtensions.applyForgeHandTransform`) applied on top of `firstperson_*`.
