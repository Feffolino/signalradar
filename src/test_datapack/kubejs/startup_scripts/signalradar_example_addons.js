// SPDX-License-Identifier: MIT
// Signal Radar example startup script: custom addons. Copy to kubejs/startup_scripts/.
// Used by ./gradlew runGameTestServerKubeJS (the KubeJS game tests check these three definitions).
//
// e.create(id, detector) with detector = 'container' | 'block_tag' | 'entity_tag' | 'structure_tag'.
// Builder (all optional except tag for the tag detectors):
//   .tag('#ns:tag')  .minTier(0..4)  .radius(minAtMinTier, maxAtTier4)  .refresh(seconds)
//   .color('#RRGGBB' or 0xRRGGBB)  .energy(FE per scan)  .category('Name')  .requiredMod('modid')
//   .icon('block:ns:id' | 'item:ns:id' | 'texture:ns:textures/..png' | 'entity:ns:id' | bare item id)  blip icon on the radar
//   .texture('ns:item/xxx')  item texture = kubejs/assets/ns/textures/item/xxx.png (default: <addon ns>:item/<addon path>)
// Item look (client side): own kubejs/assets/<ns>/models/item/<path>.json wins; else the PNG above (item/generated); else, when the
// PNG is missing, a generic icon tinted with .color() (never purple/black). The name without a lang entry is "Radar Addon (<category>)"
// (lang: kubejs/assets/<ns>/lang/en_us.json). The client log says per addon: "Custom addon <id>: texture <rl>" or "generic (texture missing)".
// A restart is needed after editing: addons are items.

SignalRadarEvents.registerAddons(event => {
  // Blocks in a tag (here a conventional oil tag; with no such blocks it simply finds nothing).
  event.create('signalradar_example:addon_oil', 'block_tag')
    .tag('#c:ores/oil')
    .minTier(3)
    .radius(16, 48)
    .refresh(5)
    .color('#222222')
    .energy(10)
    .category('Oil')
    .icon('minecraft:lava_bucket')
    // no .texture(): default signalradar_example:item/addon_oil = kubejs/assets/signalradar_example/textures/item/addon_oil.png

  // Entities in a tag.
  event.create('signalradar_example:addon_undead', 'entity_tag')
    .tag('#minecraft:undead')
    .minTier(1)
    .radius(24, 48)
    .refresh(2)
    .color(0x88AA55)
    .energy(15)
    .category('Undead')
    .texture('signalradar_example:item/undead_skull')  // kubejs/assets/signalradar_example/textures/item/undead_skull.png

  // No texture PNG anywhere: falls back to the generic tinted model.
  event.create('signalradar_example:addon_plain', 'container')
    .color('#33AAFF')
    .category('Plain')

  // Only registered when its mod is loaded (this one never is).
  event.create('signalradar_example:addon_optional', 'container')
    .requiredMod('some_missing_mod')
    .category('Optional')
})
