// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** {@code SignalRadarEvents.*}: {@code registerAddons} (startup scripts), the rest server scripts. */
public interface SignalRadarEventsJS {
    EventGroup GROUP = EventGroup.of("SignalRadarEvents");

    EventHandler REGISTER_ADDONS = GROUP.startup("registerAddons", () -> SignalRadarKubeEvents.RegisterAddons.class);
    /** Cancel ({@code event.cancel()}) = NO SIGNAL for this scan. */
    EventHandler SCAN = GROUP.server("scan", () -> SignalRadarKubeEvents.Scan.class).hasResult();
    EventHandler TARGET_FOUND = GROUP.server("targetFound", () -> SignalRadarKubeEvents.TargetFound.class);
    EventHandler UPGRADED = GROUP.server("upgraded", () -> SignalRadarKubeEvents.Upgraded.class);
    EventHandler ADDON_CHANGED = GROUP.server("addonChanged", () -> SignalRadarKubeEvents.AddonChanged.class);
}
