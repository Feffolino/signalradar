// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config {@code signalradar-server.toml}. Getters fall back to defaults before the config is loaded. */
public final class SignalRadarConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue CAPACITY;
    private static final ModConfigSpec.IntValue MAX_RECEIVE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("energy");
        CAPACITY = b.comment("FE the radar can store.").defineInRange("capacity", 20000, 1, Integer.MAX_VALUE);
        MAX_RECEIVE = b.comment("Max FE per tick the radar accepts from chargers.").defineInRange("maxReceive", 100, 0, Integer.MAX_VALUE);
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
}
