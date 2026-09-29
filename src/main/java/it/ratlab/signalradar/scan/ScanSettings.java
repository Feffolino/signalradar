// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.SignalRadarConfig;

/** The config values a scan needs, decoupled from the config so tests can pass their own. */
public record ScanSettings(int[] rangeByTier, int[] fuzzByTier, int scanCost, int refreshSeconds) {
    public static ScanSettings fromConfig() {
        return new ScanSettings(SignalRadarConfig.rangeByTier(), SignalRadarConfig.fuzzByTier(),
                SignalRadarConfig.scanCost(), SignalRadarConfig.scanRefreshSeconds());
    }

    public int range(int tier) {
        return rangeByTier[Math.max(0, Math.min(tier, rangeByTier.length - 1))];
    }

    public int fuzz(int tier) {
        return fuzzByTier[Math.max(0, Math.min(tier, fuzzByTier.length - 1))];
    }

    public int refreshTicks() {
        return Math.max(1, refreshSeconds) * 20;
    }
}
