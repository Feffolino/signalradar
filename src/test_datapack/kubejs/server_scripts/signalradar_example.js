// SPDX-License-Identifier: MIT
// Signal Radar example server script: every SignalRadarEvents server event and the SignalRadar binding.
// Copy to kubejs/server_scripts/. ./gradlew runGameTestServerKubeJS runs it; the game tests switch the branches
// on with the scoreboard tags 'sr_example_*' and read back the 'sr_js_*' tags it adds.
// Rhino notes: use let (not const) inside loops; compare levels by dimension, not with .equals.

// Java lists (event.targets, SignalRadar.targets(), ...) are read with size() / get(i).
function joinList(list, sep) {
  let out = ''
  for (let i = 0; i < list.size(); i++) {
    out += (i > 0 ? sep : '') + list.get(i)
  }
  return out
}

// scan: runs for every scan with signal, before the snapshot is sent.
//   event.player, event.radar, event.tier, event.range
//   event.targets -> list of {id, category, name, color, x, y, z, outOfRange, found}, event.targetIds -> ids
//   event.removeTarget(id)   (a target id, or an addon id = all its hits)   event.removeCategory('narrative')
//   event.removeIf(t => ...) event.addTarget(name, color, x, y, z)          event.cancel() = NO SIGNAL
SignalRadarEvents.scan(event => {
  let player = event.player
  let tags = player.tags
  if (tags.contains('sr_example_cancel')) {
    event.cancel()
  }
  if (tags.contains('sr_example_edit')) {
    event.removeTarget('signalradar:kjs_hidden')
    event.removeIf(t => t.category == 'last_death')
    event.addTarget('Script Beacon', '#FFAA00', player.x + 10, player.y, player.z)
    let targets = event.targets
    let names = []
    for (let i = 0; i < targets.size(); i++) {
      let t = targets.get(i)
      names.push(t.id + '=' + t.category)
    }
    player.addTag('sr_js_scan:' + event.tier + ':' + names.join(','))
  }
  if (tags.contains('sr_example_binding')) {
    let radar = event.radar
    let id = 'signalradar:kjs_locked'
    SignalRadar.setEnergy(radar, 1234)
    SignalRadar.unlock(player, id)
    let unlocked = SignalRadar.isUnlocked(player, id)
    SignalRadar.lock(player, id)
    let locked = !SignalRadar.isUnlocked(player, id)
    let pos = SignalRadar.getTargetPos(player.level, 'signalradar:kjs_hidden')
    let where = pos == null ? 'none' : Math.round(pos.x()) + '/' + Math.round(pos.z())
    player.addTag('sr_js_binding:' + [
      SignalRadar.isRadar(radar),
      SignalRadar.getTier(radar),
      SignalRadar.getEnergy(radar),
      unlocked,
      locked,
      SignalRadar.isFound(player, 'signalradar:kjs_hidden'),
      SignalRadar.hasAddon(radar, 'signalradar:addon_ore'),
      joinList(SignalRadar.getAddons(radar), '+'),
      joinList(SignalRadar.targets(), '+'),
      where
    ].join(':'))
    SignalRadar.setTier(radar, 4)
  }
})

// targetFound: a player found a narrative target (stage signalradar_found_<path> is already set).
SignalRadarEvents.targetFound(event => {
  let player = event.player
  player.addTag('sr_js_found:' + event.targetId + ':' + event.targetName)
  let path = event.targetId.split(':')[1]
  if (player.stages.has('signalradar_found_' + path)) {
    player.addTag('sr_js_found_stage:' + path)
  }
  if (player.tags.contains('sr_example_binding')) {
    SignalRadar.resetFound(player, event.targetId)
  }
})

// upgraded: a radar came out of the upgrade recipe.
SignalRadarEvents.upgraded(event => {
  event.player.addTag('sr_js_upgraded:' + event.oldTier + '>' + event.newTier + ':' + SignalRadar.getTier(event.radar))
})

// addonChanged: an addon slot changed (GUI or /signalradar addon). Ids, null for an empty slot.
SignalRadarEvents.addonChanged(event => {
  event.player.addTag('sr_js_addon:' + event.slot + ':' + (event.oldAddon || 'none') + '>' + (event.newAddon || 'none'))
})
