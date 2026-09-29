// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import java.util.List;

/**
 * Everything the client display needs for one scan.
 *
 * @param tier      radar tier 0..4
 * @param energy    FE left after this scan's charge
 * @param capacity  FE capacity
 * @param range     display radius in blocks (the server's range for this tier)
 * @param refreshSeconds seconds until the next scan (client stale check: older than twice this = no data)
 * @param noSignal  the radar could not pay for the scan (blips is empty)
 * @param gameTime  server game time of the scan (client uses it for staleness)
 * @param charged   this snapshot paid a base scan (the client pings only then; addon refreshes in between are free)
 * @param motionRadius radius of the installed motion addon at this tier, 0 without one (the client scales the motion beep by it)
 */
public record ScanSnapshot(int tier, int energy, int capacity, int range, int refreshSeconds, boolean noSignal, long gameTime, List<Blip> blips,
                           boolean charged, int motionRadius) {
    public ScanSnapshot {
        blips = List.copyOf(blips);
    }

    /** No motion addon. */
    public ScanSnapshot(int tier, int energy, int capacity, int range, int refreshSeconds, boolean noSignal, long gameTime, List<Blip> blips,
                        boolean charged) {
        this(tier, energy, capacity, range, refreshSeconds, noSignal, gameTime, blips, charged, 0);
    }

    /** A charged scan. */
    public ScanSnapshot(int tier, int energy, int capacity, int range, int refreshSeconds, boolean noSignal, long gameTime, List<Blip> blips) {
        this(tier, energy, capacity, range, refreshSeconds, noSignal, gameTime, blips, true);
    }
}
