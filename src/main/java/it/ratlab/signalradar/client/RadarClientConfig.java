// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config {@code config/signalradar-client.toml}. Getters fall back to defaults before it is loaded. */
public final class RadarClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue MOTION_BEEP = B
            .comment("Motion tracker addon: beep faster as moving hostiles get closer.")
            .define("motionBeep", true);

    private static final ModConfigSpec.DoubleValue SCREEN_BRIGHTNESS = B
            .comment("Brightness of the radar display drawn on the item (0.2 = dim, 1.0 = full).")
            .defineInRange("screenBrightness", 1.0, 0.2, 1.0);

    private static final ModConfigSpec.BooleanValue SHOW_HEIGHT_ARROWS = B
            .comment("Show a small up/down arrow next to blips more than 4 blocks above/below you.")
            .define("showHeightArrows", true);

    public static final ModConfigSpec SPEC = B.build();

    private RadarClientConfig() {}

    public static boolean motionBeep() {
        return SPEC.isLoaded() ? MOTION_BEEP.getAsBoolean() : MOTION_BEEP.getDefault();
    }

    public static float screenBrightness() {
        double v = SPEC.isLoaded() ? SCREEN_BRIGHTNESS.getAsDouble() : SCREEN_BRIGHTNESS.getDefault();
        return (float) Math.max(0.2, Math.min(1.0, v));
    }

    public static boolean showHeightArrows() {
        return SPEC.isLoaded() ? SHOW_HEIGHT_ARROWS.getAsBoolean() : SHOW_HEIGHT_ARROWS.getDefault();
    }
}
