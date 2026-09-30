// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.SignalRadarConfig;

/** The config values a scan needs, decoupled from the config so tests can pass their own. */
public record ScanSettings(int[] rangeByTier, int[] fuzzByTier, int scanCost, int refreshSeconds, double[] energyMultiplierByTier) {
    /** No tier discount (every multiplier 1.0). */
    public ScanSettings(int[] rangeByTier, int[] fuzzByTier, int scanCost, int refreshSeconds) {
        this(rangeByTier, fuzzByTier, scanCost, refreshSeconds, new double[] {1, 1, 1, 1, 1});
    }

    public static ScanSettings fromConfig() {
        return new ScanSettings(SignalRadarConfig.rangeByTier(), SignalRadarConfig.fuzzByTier(),
                SignalRadarConfig.scanCost(), SignalRadarConfig.scanRefreshSeconds(), SignalRadarConfig.energyMultiplierByTier());
    }

    /** Same settings with another snapshot refresh period (the send interval when addons refresh faster). */
    public ScanSettings withRefreshSeconds(int seconds) {
        return new ScanSettings(rangeByTier, fuzzByTier, scanCost, seconds, energyMultiplierByTier);
    }

    /** Energy multiplier of a tier (higher tiers are more efficient). */
    public double energyMultiplier(int tier) {
        return energyMultiplierByTier[Math.max(0, Math.min(tier, energyMultiplierByTier.length - 1))];
    }

    /** FE charged per period: {@code ceil((scanCost + addonCost) * multiplier[tier])}. */
    public int charge(int tier, int addonCost) {
        return chargeFor((long) scanCost + addonCost, energyMultiplier(tier));
    }

    /** {@code ceil(base * multiplier)}, immune to binary rounding (100 * 0.85 must be 85, not 86). */
    public static int chargeFor(long base, double multiplier) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (long) Math.ceil(base * multiplier - 1e-9)));
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
