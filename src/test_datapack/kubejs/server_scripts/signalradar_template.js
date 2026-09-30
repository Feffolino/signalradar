// SPDX-License-Identifier: MIT
// Signal Radar server script template: copy to kubejs/server_scripts/ and keep what you need.
// Every handler below is switched off (ENABLED = false) so the file is harmless as shipped; set it to true to try them.
// Rhino notes: use let (not const) inside loops; Java lists are read with size() / get(i); compare levels by dimension.
// Ids without a namespace get 'kubejs:'. Bad ids never throw: the binding methods return false / null / do nothing and
// log a warning.

let ENABLED = false

// scan: runs for every scan that has signal, before the snapshot is sent to the player.
//   event.player, event.radar, event.tier, event.range
//   event.targets    -> list of {id, category, name, color, x, y, z, outOfRange, found}
//   event.targetIds  -> list of ids
//   event.removeTarget('ns:target_or_addon')   event.removeCategory('narrative')
//   event.removeIf(t => t.outOfRange)          event.addTarget('Name', '#FFAA00', x, y, z)
//   event.cancel()  -> NO SIGNAL for this scan
SignalRadarEvents.scan(event => {
  if (!ENABLED) return
  // Hide a target until the player has its stage.
  if (!event.player.stages.has('my_stage')) {
    event.removeTarget('my_pack:secret_target')
  }
  // Add a scripted blip ten blocks east of the player.
  event.addTarget('Supply Drop', '#FFAA00', event.player.x + 10, event.player.y, event.player.z)
  // Jam the radar during a storm.
  if (event.player.level.raining && event.tier < 3) {
    event.cancel()
  }
})

// targetFound: a player found a narrative target (its stage signalradar_found_<path> is already set).
//   event.player, event.targetId, event.target, event.targetName
SignalRadarEvents.targetFound(event => {
  if (!ENABLED) return
  event.player.tell('Found: ' + event.targetName)
})

// upgraded: a radar came out of the upgrade recipe. event.player, event.radar, event.oldTier, event.newTier
SignalRadarEvents.upgraded(event => {
  if (!ENABLED) return
  event.player.tell('Radar upgraded to tier ' + event.newTier)
})

// addonChanged: an addon slot changed (GUI or /signalradar addon). event.player, event.radar, event.slot,
// event.oldAddon, event.newAddon (ids, null = empty slot)
SignalRadarEvents.addonChanged(event => {
  if (!ENABLED) return
  event.player.tell('Slot ' + event.slot + ': ' + (event.oldAddon || 'empty') + ' -> ' + (event.newAddon || 'empty'))
})

// SignalRadar binding (server scripts only; all ids are strings).
//   Radar stack:  SignalRadar.isRadar(stack)  getTier(stack) setTier(stack, n)  getEnergy(stack) setEnergy(stack, fe)
//                 getAddons(stack) -> list of ids   hasAddon(stack, 'ns:addon')
//   Player:       SignalRadar.unlock(player, 'ns:target')  lock(...)  isUnlocked(...)  isFound(...)
//                 resetFound(player, 'ns:target')  resetFound(player, 'all')
//   Targets:      SignalRadar.getTargetPos(level, 'ns:target') -> Vec3 or null    SignalRadar.targets() -> sorted ids
PlayerEvents.chat(event => {
  if (!ENABLED) return
  if (event.message == 'radar?') {
    let stack = event.player.mainHandItem
    if (SignalRadar.isRadar(stack)) {
      event.player.tell('Tier ' + SignalRadar.getTier(stack) + ', ' + SignalRadar.getEnergy(stack) + ' FE')
    }
    SignalRadar.unlock(event.player, 'my_pack:secret_target')
  }
})
