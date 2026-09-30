// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import java.util.List;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config {@code signalradar-server.toml}. Getters fall back to defaults before the config is loaded. */
public final class SignalRadarConfig {
    public static final int TIERS = 5;
    public static final List<Integer> DEFAULT_RANGE = List.of(256, 512, 1024, 2048, 4096);
    public static final List<Integer> DEFAULT_FUZZ = List.of(64, 32, 16, 6, 0);
    public static final List<Double> DEFAULT_ENERGY_MULTIPLIER = List.of(1.0, 0.85, 0.7, 0.55, 0.4);
    public static final double MIN_ENERGY_MULTIPLIER = 0.05;

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CAPACITY;
    private static final ModConfigSpec.IntValue MAX_RECEIVE;
    private static final ModConfigSpec.IntValue SCAN_COST;
    private static final ModConfigSpec.IntValue SCAN_REFRESH_SECONDS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> RANGE_BY_TIER;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> FUZZ_BY_TIER;
    private static final ModConfigSpec.ConfigValue<List<? extends Number>> ENERGY_MULTIPLIER_BY_TIER;
    private static final ModConfigSpec.IntValue STRUCTURE_LOOKUPS_PER_TICK;
    private static final ModConfigSpec.IntValue MAX_SCANNABLE_STRUCTURES;
    private static final ModConfigSpec.IntValue MAX_BLOCK_CHECKS_PER_SCAN;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SLOTS_BY_TIER;
    public static final List<Integer> DEFAULT_SLOTS = List.of(1, 2, 3, 4, 5);
    /** The addon menu has room for this many slots. */
    public static final int MAX_ADDON_SLOTS = 5;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("energy");
        CAPACITY = b.comment("FE the radar can store.").defineInRange("capacity", 20000, 1, Integer.MAX_VALUE);
        MAX_RECEIVE = b.comment("Max FE per tick the radar accepts from chargers.").defineInRange("maxReceive", 100, 0, Integer.MAX_VALUE);
        b.pop();
        b.push("scan");
        SCAN_COST = b.comment("FE charged per scan while a radar is held. Not enough FE = NO SIGNAL.")
                .defineInRange("scanCost", 50, 0, Integer.MAX_VALUE);
        SCAN_REFRESH_SECONDS = b.comment("Seconds between two scans of a held radar.")
                .defineInRange("scanRefreshSeconds", 5, 1, 3600);
        RANGE_BY_TIER = b.comment("Detection range in blocks for tiers 0-4 (exactly 5 values, otherwise the defaults are used).")
                .defineList("rangeByTier", DEFAULT_RANGE, () -> 256, o -> o instanceof Integer);
        FUZZ_BY_TIER = b.comment("Maximum position fuzz in blocks for tiers 0-4 (exactly 5 values, otherwise the defaults are used).")
                .defineList("fuzzByTier", DEFAULT_FUZZ, () -> 0, o -> o instanceof Integer);
        ENERGY_MULTIPLIER_BY_TIER = b.comment("Multiplier on the FE charged per scan period for tiers 0-4: the charge is",
                        "ceil((scanCost + addon costs) * multiplier). Higher tiers are more efficient. Exactly 5 values, 0.05-1.0 each,",
                        "otherwise the defaults are used.")
                .defineList("energyMultiplierByTier", DEFAULT_ENERGY_MULTIPLIER, () -> 1.0, o -> o instanceof Number);
        STRUCTURE_LOOKUPS_PER_TICK = b.comment("Max structure searches (findNearestMapStructure) the server runs per tick.")
                .defineInRange("structureLookupsPerTick", 1, 1, 64);
        MAX_SCANNABLE_STRUCTURES = b.comment("Max entries read from the scannable structures tag (used by the structure addon).")
                .defineInRange("maxScannableStructures", 16, 1, 256);
        MAX_BLOCK_CHECKS_PER_SCAN = b.comment("Max block states the 'block' locators may test in one player scan (all block targets together).",
                        "Each 16x16x16 chunk section that may contain the block costs 4096. When the budget runs out the search",
                        "stops and keeps the nearest block found so far.")
                .defineInRange("maxBlockChecksPerScan", 200000, 4096, Integer.MAX_VALUE);
        b.pop();
        b.push("addons");
        SLOTS_BY_TIER = b.comment("Addon slots for tiers 0-4 (exactly 5 values, 0-5 each, otherwise the defaults are used).")
                .defineList("slotsByTier", DEFAULT_SLOTS, () -> 1, o -> o instanceof Integer);
        it.ratlab.signalradar.addon.AddonConfig.define(b);
        b.pop();
        SPEC = b.build();
    }

    private SignalRadarConfig() {}

    public static int capacity() {
        return SPEC.isLoaded() ? CAPACITY.get() : CAPACITY.getDefault();
    }

    public static int maxReceive() {
        return SPEC.isLoaded() ? MAX_RECEIVE.get() : MAX_RECEIVE.getDefault();
    }

    public static int scanCost() {
        return SPEC.isLoaded() ? SCAN_COST.get() : SCAN_COST.getDefault();
    }

    public static int scanRefreshSeconds() {
        return SPEC.isLoaded() ? SCAN_REFRESH_SECONDS.get() : SCAN_REFRESH_SECONDS.getDefault();
    }

    public static int structureLookupsPerTick() {
        return SPEC.isLoaded() ? STRUCTURE_LOOKUPS_PER_TICK.get() : STRUCTURE_LOOKUPS_PER_TICK.getDefault();
    }

    public static int maxScannableStructures() {
        return SPEC.isLoaded() ? MAX_SCANNABLE_STRUCTURES.get() : MAX_SCANNABLE_STRUCTURES.getDefault();
    }

    public static int maxBlockChecksPerScan() {
        return SPEC.isLoaded() ? MAX_BLOCK_CHECKS_PER_SCAN.get() : MAX_BLOCK_CHECKS_PER_SCAN.getDefault();
    }

    public static int[] rangeByTier() {
        return tierList(RANGE_BY_TIER, DEFAULT_RANGE, "rangeByTier");
    }

    public static int[] fuzzByTier() {
        return tierList(FUZZ_BY_TIER, DEFAULT_FUZZ, "fuzzByTier");
    }

    /** Energy multiplier per tier, each clamped to 0.05..1.0 (five values, otherwise the defaults). */
    public static double[] energyMultiplierByTier() {
        List<? extends Number> list = SPEC.isLoaded() ? ENERGY_MULTIPLIER_BY_TIER.get() : DEFAULT_ENERGY_MULTIPLIER;
        return sanitizeMultipliers(list);
    }

    public static double[] sanitizeMultipliers(List<? extends Number> list) {
        if (list == null || list.size() != TIERS) {
            if (!warnedMultiplier) {
                warnedMultiplier = true;
                SignalRadar.LOGGER.warn("Config 'energyMultiplierByTier' needs exactly {} values (got {}); using defaults {}", TIERS,
                        list == null ? "null" : list.size(), DEFAULT_ENERGY_MULTIPLIER);
            }
            list = DEFAULT_ENERGY_MULTIPLIER;
        }
        double[] out = new double[TIERS];
        for (int i = 0; i < TIERS; i++) {
            double v = list.get(i).doubleValue();
            out[i] = Double.isNaN(v) ? 1.0 : Math.max(MIN_ENERGY_MULTIPLIER, Math.min(1.0, v));
        }
        return out;
    }

    private static boolean warnedMultiplier;

    /** Addon slots per tier, each clamped to 0..{@link #MAX_ADDON_SLOTS}. */
    public static int[] slotsByTier() {
        int[] v = tierList(SLOTS_BY_TIER, DEFAULT_SLOTS, "slotsByTier");
        for (int i = 0; i < v.length; i++) {
            v[i] = Math.min(v[i], MAX_ADDON_SLOTS);
        }
        return v;
    }

    private static boolean warned;

    private static int[] tierList(ModConfigSpec.ConfigValue<List<? extends Integer>> value, List<Integer> defaults, String name) {
        List<? extends Integer> list = SPEC.isLoaded() ? value.get() : defaults;
        return sanitizeTierList(list, defaults, name);
    }

    /** Exactly {@link #TIERS} non-negative values, otherwise the defaults (with a one-time warning). */
    public static int[] sanitizeTierList(List<? extends Integer> list, List<Integer> defaults, String name) {
        if (list == null || list.size() != TIERS) {
            if (!warned) {
                warned = true;
                SignalRadar.LOGGER.warn("Config '{}' needs exactly {} values (got {}); using defaults {}", name, TIERS,
                        list == null ? "null" : list.size(), defaults);
            }
            list = defaults;
        }
        int[] out = new int[TIERS];
        for (int i = 0; i < TIERS; i++) {
            out[i] = Math.max(0, Mth.clamp(list.get(i), 0, Integer.MAX_VALUE));
        }
        return out;
    }
}
