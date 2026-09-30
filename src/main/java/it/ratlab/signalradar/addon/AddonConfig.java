// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import it.ratlab.signalradar.SignalRadarConfig;
import java.util.HashMap;
import java.util.List;
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
    private static ModConfigSpec.ConfigValue<List<? extends String>> oreOverrides;
    private static ModConfigSpec.BooleanValue includeLootr;
    private static ModConfigSpec.IntValue stationaryFrom;
    private static ModConfigSpec.IntValue batteryCapacity;
    /** Default of {@code addons.battery.capacityPerBattery}. */
    public static final int DEFAULT_BATTERY_CAPACITY = 10_000;
    /** Game tests only: forces {@code capacityPerBattery} (-1 = follow the config). */
    private static volatile int batteryOverride = -1;
    /** Game tests only: forces {@code stationaryFromTier} (-1 = follow the config). */
    private static volatile int stationaryOverride = -1;
    /** Game tests only: forces the value of {@code includeLootrContainers} (null = follow the config). */
    private static volatile Boolean lootrOverride;

    private AddonConfig() {}

    /** Adds one section per built-in addon to the current builder position. */
    public static void define(ModConfigSpec.Builder b) {
        for (AddonDefinition d : AddonRegistry.builtins()) {
            String name = d.id().getPath().replaceFirst("^addon_", "");
            b.push(name);
            if (d.detector() == AddonDefinition.Detector.NONE) {
                // Battery: no detection, so no radius / refresh / colour / energy keys.
                ModConfigSpec.BooleanValue enabled = b.comment("Addon can be installed (disabled batteries add no capacity).").define("enabled", true);
                ModConfigSpec.IntValue minTier = b.comment("Radar tier needed to install it (0-4).")
                        .defineInRange("minTier", d.minTier(), 0, AddonMath.MAX_TIER);
                batteryCapacity = b.comment("FE capacity each battery adds to the radar (a slot holds up to " + AddonRegistry.BATTERY_STACK + ").",
                                "Radar capacity = energy.capacity + batteries * capacityPerBattery. Removing batteries clamps the stored",
                                "energy to the new capacity (the extra FE is lost).")
                        .defineInRange("capacityPerBattery", DEFAULT_BATTERY_CAPACITY, 0, 100_000_000);
                b.pop();
                VALUES.put(d.id(), new Values(enabled, minTier, null, null, null, null, null));
                continue;
            }
            ModConfigSpec.BooleanValue enabled = b.comment("Addon can be installed and scanned.").define("enabled", true);
            ModConfigSpec.IntValue minTier = b.comment("Radar tier needed to install it (0-4).")
                    .defineInRange("minTier", d.minTier(), 0, AddonMath.MAX_TIER);
            ModConfigSpec.IntValue radiusMin = b.comment("Radius in blocks at min tier (0 with radiusMax 0 = the radar's tier range).")
                    .defineInRange("radiusMin", d.radiusMin(), 0, 100_000);
            ModConfigSpec.IntValue radiusMax = b.comment("Radius in blocks at tier 4 (grows linearly from min tier).")
                    .defineInRange("radiusMax", d.radiusMax(), 0, 100_000);
            ModConfigSpec.IntValue refresh = b.comment("Seconds between two detections (results are cached until then).")
                    .defineInRange("refreshSeconds", d.refreshSeconds(), 1, 3600);
            ModConfigSpec.ConfigValue<String> color = b.comment("Blip colour #RRGGBB (ore blips use the ore material colour, this is the fallback).")
                    .define("color", AddonMath.formatColor(d.color()), o -> o instanceof String s && AddonMath.parseColor(s, -1) >= 0);
            ModConfigSpec.IntValue energy = b.comment("FE added to each base scan charge while this addon is installed.")
                    .defineInRange("energyCost", d.energyCost(), 0, 1_000_000);
            if (d.id().equals(AddonRegistry.CONTAINER)) {
                includeLootr = b.comment("Also show Lootr containers (only matters with Lootr installed).",
                                "Turn off when the Loot addon is used, so the container addon shows only ordinary containers.")
                        .define("includeLootrContainers", true);
            }
            if (d.id().equals(AddonRegistry.MOTION)) {
                stationaryFrom = b.comment("Radar tier from which the motion tracker also shows stationary hostiles (dark red, no pulse, no beep).",
                                "5 = never: only moving hostiles are shown.")
                        .defineInRange("stationaryFromTier", 3, 0, AddonMath.STATIONARY_NEVER);
            }
            if (d.useMapColor()) {
                oreOverrides = b.comment("Ore blip colours per material, applied over the built-in table: \"material=#RRGGBB\".",
                                "The material is the name after c:ores/ in the block's tag (iron, gold, osmium, ...); unknown materials get a stable hash colour.")
                        .defineListAllowEmpty("colorOverrides", List.of(), () -> "iron=#D8AF93", o -> o instanceof String);
            }
            b.pop();
            VALUES.put(d.id(), new Values(enabled, minTier, radiusMin, radiusMax, refresh, color, energy));
        }
    }

    /** {@code addons.ore.colorOverrides} strings; empty before the config is loaded. Same instance until the config changes. */
    public static List<? extends String> oreColorOverrides() {
        return oreOverrides == null || !SignalRadarConfig.SPEC.isLoaded() ? List.of() : oreOverrides.get();
    }

    /** {@code addons.container.includeLootrContainers}; true before the config is loaded. */
    public static boolean includeLootrContainers() {
        if (lootrOverride != null) {
            return lootrOverride;
        }
        return includeLootr == null || !SignalRadarConfig.SPEC.isLoaded() || includeLootr.get();
    }

    /** {@code addons.motion.stationaryFromTier}; 3 before the config is loaded. */
    public static int stationaryFromTier() {
        if (stationaryOverride >= 0) {
            return stationaryOverride;
        }
        return stationaryFrom == null || !SignalRadarConfig.SPEC.isLoaded() ? 3 : stationaryFrom.get();
    }

    public static void overrideStationaryFromTier(int value) {
        stationaryOverride = value;
    }

    public static void overrideIncludeLootrContainers(Boolean value) {
        lootrOverride = value;
    }

    /** {@code addons.battery.capacityPerBattery}; {@value #DEFAULT_BATTERY_CAPACITY} before the config is loaded. */
    public static int capacityPerBattery() {
        if (batteryOverride >= 0) {
            return batteryOverride;
        }
        return batteryCapacity == null || !SignalRadarConfig.SPEC.isLoaded() ? DEFAULT_BATTERY_CAPACITY : batteryCapacity.get();
    }

    public static void overrideCapacityPerBattery(int value) {
        batteryOverride = value;
    }

    public static AddonSettings settings(AddonDefinition def) {
        Values v = VALUES.get(def.id());
        if (v == null || !SignalRadarConfig.SPEC.isLoaded()) {
            return AddonSettings.defaults(def);
        }
        if (v.radiusMin == null) {
            AddonSettings d = AddonSettings.defaults(def);
            return new AddonSettings(def, v.enabled.get(), v.minTier.get(), d.radiusMin(), d.radiusMax(), d.refreshSeconds(), d.color(),
                    d.energyCost());
        }
        int min = v.radiusMin.get();
        int max = Math.max(min, v.radiusMax.get());
        return new AddonSettings(def, v.enabled.get(), v.minTier.get(), min, max, v.refresh.get(),
                AddonMath.parseColor(v.color.get(), def.color()), v.energy.get());
    }
}
