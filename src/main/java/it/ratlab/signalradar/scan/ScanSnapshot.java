// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import java.util.List;

/**
 * Everything the client display needs for one scan.
 *
 * @param tier      radar tier 0..4
 * @param energy    FE left after this scan's charge
 * @param capacity  FE capacity
 * @param noSignal  the radar could not pay for the scan (blips is empty)
 * @param gameTime  server game time of the scan (client uses it for staleness)
 */
public record ScanSnapshot(int tier, int energy, int capacity, boolean noSignal, long gameTime, List<Blip> blips) {
    public ScanSnapshot {
        blips = List.copyOf(blips);
    }
}
