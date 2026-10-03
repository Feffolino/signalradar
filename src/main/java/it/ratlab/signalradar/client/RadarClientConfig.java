// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client config {@code config/signalradar-client.toml}. Getters fall back to defaults before it is loaded. */
public final class RadarClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue MOTION_BEEP = B
            .comment("Motion tracker addon: beep faster as moving hostiles get closer.")
            .define("motionBeep", true);

    private static final ForgeConfigSpec.DoubleValue SCREEN_BRIGHTNESS = B
            .comment("Brightness of the radar display drawn on the item (0.2 = dim, 1.0 = full).")
            .defineInRange("screenBrightness", 1.0, 0.2, 1.0);

    private static final ForgeConfigSpec.BooleanValue SHOW_HEIGHT_ARROWS = B
            .comment("Show a small up/down arrow next to blips more than 4 blocks above/below you.")
            .define("showHeightArrows", true);

    private static final ForgeConfigSpec.DoubleValue ICON_SIZE = B
            .comment("Size factor of the blip icons (1.0 = about 12 icons across the screen).")
            .defineInRange("iconSize", 1.0, 0.5, 2.0);

    private static final ForgeConfigSpec.IntValue MAX_ICONS = B
            .comment("At most this many of the nearest blips are drawn as icons, the rest as plain dots (0 = dots only).")
            .defineInRange("maxIcons", 48, 0, 256);

    private static final ForgeConfigSpec.IntValue ZOOM_RANGE = B
            .comment("Last display range chosen with the zoom (metres), saved across restarts. 0 = follow the tier range.",
                    "A radar whose tier can't reach it shows its own tier range; the saved value is kept.")
            .defineInRange("zoomRange", 0, 0, 1 << 20);

    public static final ForgeConfigSpec SPEC = B.build();

    public static int zoomRange() {
        return SPEC.isLoaded() ? ZOOM_RANGE.get() : ZOOM_RANGE.getDefault();
    }

    /** Writes the zoom choice to the client config file (called debounced / on logout / on shutdown, not per step). */
    public static void saveZoomRange(int range) {
        if (SPEC.isLoaded() && ZOOM_RANGE.get() != range) {
            ZOOM_RANGE.set(range);
            ZOOM_RANGE.save();
        }
    }

    private RadarClientConfig() {}

    public static boolean motionBeep() {
        return SPEC.isLoaded() ? MOTION_BEEP.get() : MOTION_BEEP.getDefault();
    }

    public static float screenBrightness() {
        double v = SPEC.isLoaded() ? SCREEN_BRIGHTNESS.get() : SCREEN_BRIGHTNESS.getDefault();
        return (float) Math.max(0.2, Math.min(1.0, v));
    }

    public static boolean showHeightArrows() {
        return SPEC.isLoaded() ? SHOW_HEIGHT_ARROWS.get() : SHOW_HEIGHT_ARROWS.getDefault();
    }

    public static float iconSize() {
        double v = SPEC.isLoaded() ? ICON_SIZE.get() : ICON_SIZE.getDefault();
        return (float) Math.max(0.5, Math.min(2.0, v));
    }

    public static int maxIcons() {
        int v = SPEC.isLoaded() ? MAX_ICONS.get() : MAX_ICONS.getDefault();
        return Math.max(0, Math.min(256, v));
    }
}
