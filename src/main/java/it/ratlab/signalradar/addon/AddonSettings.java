// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

/**
 * Effective values of an addon at use time: server config for built-ins, the definition for custom addons.
 */
public record AddonSettings(AddonDefinition def, boolean enabled, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                            int color, int energyCost) {

    public static AddonSettings of(AddonDefinition def) {
        return AddonConfig.settings(def);
    }

    public static AddonSettings defaults(AddonDefinition def) {
        return new AddonSettings(def, true, def.minTier(), def.radiusMin(), def.radiusMax(), def.refreshSeconds(), def.color(),
                def.energyCost());
    }

    /** Detection radius at {@code tier}; {@code tierRange} when both radii are 0 (structure addon). */
    public int radius(int tier, int tierRange) {
        if (radiusMin <= 0 && radiusMax <= 0) {
            return tierRange;
        }
        return AddonMath.radius(radiusMin, radiusMax, minTier, tier);
    }

    /** Enabled and the radar tier is high enough. */
    public boolean usableAt(int tier) {
        return enabled && minTier <= tier;
    }

    public boolean usesTierRange() {
        return radiusMin <= 0 && radiusMax <= 0;
    }
}
