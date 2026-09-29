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

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CAPACITY;
    private static final ModConfigSpec.IntValue MAX_RECEIVE;
    private static final ModConfigSpec.IntValue SCAN_COST;
    private static final ModConfigSpec.IntValue SCAN_REFRESH_SECONDS;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> RANGE_BY_TIER;
    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> FUZZ_BY_TIER;
    private static final ModConfigSpec.IntValue STRUCTURE_LOOKUPS_PER_TICK;
    private static final ModConfigSpec.IntValue MAX_SCANNABLE_STRUCTURES;

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
        STRUCTURE_LOOKUPS_PER_TICK = b.comment("Max structure searches (findNearestMapStructure) the server runs per tick.")
                .defineInRange("structureLookupsPerTick", 1, 1, 64);
        MAX_SCANNABLE_STRUCTURES = b.comment("Max entries read from the scannable structures tag (used by the structure addon).")
                .defineInRange("maxScannableStructures", 16, 1, 256);
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

    public static int[] rangeByTier() {
        return tierList(RANGE_BY_TIER, DEFAULT_RANGE, "rangeByTier");
    }

    public static int[] fuzzByTier() {
        return tierList(FUZZ_BY_TIER, DEFAULT_FUZZ, "fuzzByTier");
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
