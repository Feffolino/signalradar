// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import it.ratlab.signalradar.SignalRadarConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config entries {@code addons.<name>.*} of the built-in addons (custom addons use their definition values).
 * Defined from {@link SignalRadarConfig}'s builder; values are read at use time.
 */
public final class AddonConfig {
    private record Values(ModConfigSpec.BooleanValue enabled, ModConfigSpec.IntValue minTier, ModConfigSpec.IntValue radiusMin,
                          ModConfigSpec.IntValue radiusMax, ModConfigSpec.IntValue refresh,
                          ModConfigSpec.ConfigValue<String> color, ModConfigSpec.IntValue energy) {}

    private static final Map<ResourceLocation, Values> VALUES = new HashMap<>();

    private AddonConfig() {}

    /** Adds one section per built-in addon to the current builder position. */
    public static void define(ModConfigSpec.Builder b) {
        for (AddonDefinition d : AddonRegistry.builtins()) {
            String name = d.id().getPath().replaceFirst("^addon_", "");
            b.push(name);
            ModConfigSpec.BooleanValue enabled = b.comment("Addon can be installed and scanned.").define("enabled", true);
            ModConfigSpec.IntValue minTier = b.comment("Radar tier needed to install it (0-4).")
                    .defineInRange("minTier", d.minTier(), 0, AddonMath.MAX_TIER);
            ModConfigSpec.IntValue radiusMin = b.comment("Radius in blocks at min tier (0 with radiusMax 0 = the radar's tier range).")
                    .defineInRange("radiusMin", d.radiusMin(), 0, 100_000);
            ModConfigSpec.IntValue radiusMax = b.comment("Radius in blocks at tier 4 (grows linearly from min tier).")
                    .defineInRange("radiusMax", d.radiusMax(), 0, 100_000);
            ModConfigSpec.IntValue refresh = b.comment("Seconds between two detections (results are cached until then).")
                    .defineInRange("refreshSeconds", d.refreshSeconds(), 1, 3600);
            ModConfigSpec.ConfigValue<String> color = b.comment("Blip colour #RRGGBB (ore blips use the block's map colour, this is the fallback).")
                    .define("color", AddonMath.formatColor(d.color()), o -> o instanceof String s && AddonMath.parseColor(s, -1) >= 0);
            ModConfigSpec.IntValue energy = b.comment("FE added to each base scan charge while this addon is installed.")
                    .defineInRange("energyCost", d.energyCost(), 0, 1_000_000);
            b.pop();
            VALUES.put(d.id(), new Values(enabled, minTier, radiusMin, radiusMax, refresh, color, energy));
        }
    }

    public static AddonSettings settings(AddonDefinition def) {
        Values v = VALUES.get(def.id());
        if (v == null || !SignalRadarConfig.SPEC.isLoaded()) {
            return AddonSettings.defaults(def);
        }
        int min = v.radiusMin.get();
        int max = Math.max(min, v.radiusMax.get());
        return new AddonSettings(def, v.enabled.get(), v.minTier.get(), min, max, v.refresh.get(),
                AddonMath.parseColor(v.color.get(), def.color()), v.energy.get());
    }
}
