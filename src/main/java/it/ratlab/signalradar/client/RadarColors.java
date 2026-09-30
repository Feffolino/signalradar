// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

/** Every colour of the radar display (0xRRGGBB). Fades are mixed against {@link #DISC} in code. */
public final class RadarColors {
    /** Whole screen rectangle behind everything. */
    public static final int SCREEN_BG = 0x030A05;
    /** Radar disc (dark phosphor). */
    public static final int DISC = 0x07190C;
    /** Range rings and the outer rim. */
    public static final int RING = 0x1C5A2A;
    public static final int RIM = 0x2E8C42;
    /** Sweep line head and the colour its trail fades from. */
    public static final int SWEEP = 0x7CFF8C;
    public static final int TRAIL = 0x2FAF4A;
    /** North marker on the rim. */
    public static final int NORTH = 0xE8FFE8;
    /** Rim arrows of targets outside the display radius are drawn in the blip colour; this is their outline tint. */
    public static final int ARROW_DIM = 0x0E3318;
    /** Height arrows next to blips. */
    public static final int HEIGHT_ARROW = 0xCFFFD6;
    /** Dark square behind blip icons. */
    public static final int ICON_BG = 0x04100A;
    /** Found targets: blip colour mixed this much toward the disc, plus the check mark colour. */
    public static final double FOUND_DIM = 0.65;
    public static final int FOUND_CHECK = 0xB8FFC4;

    /** Side panel: tier pips, energy bar. */
    public static final int PANEL_BG = 0x051208;
    public static final int PIP_ON = 0x7CFF8C;
    public static final int PIP_OFF = 0x123A1C;
    public static final int ENERGY_OK = 0x39FF5A;
    public static final int ENERGY_LOW = 0xFFB020;
    public static final int ENERGY_EMPTY = 0xFF3030;
    public static final int ENERGY_BAR_BG = 0x0C2412;
    public static final int RANGE_TEXT = 0x6FDF80;

    /** Raised text line. */
    public static final int TEXT_BG = 0x020803;
    public static final int TEXT = 0xB8FFC4;

    /** NO SIGNAL: static noise greys and the label. */
    public static final int STATIC_DARK = 0x101410;
    public static final int STATIC_LIGHT = 0x6A7A6A;
    public static final int NO_SIGNAL_TEXT = 0xFF4040;

    /** Status LED. */
    public static final int LED_OK = 0x30FF50;
    public static final int LED_LOW = 0xFFB020;
    public static final int LED_NO_SIGNAL = 0xFF2020;
    public static final int LED_OFF = 0x301010;

    /** Motion tracker blips (category {@code motion}): default colour, and the lowest brightness of the pulse. */
    public static final String MOTION_CATEGORY = "motion";
    public static final int MOTION = 0xFF3030;
    public static final double MOTION_PULSE_MIN = 0.35;
    /** Still hostiles of the motion tracker (category {@code motion_still}): steady dim red, no pulse, no sound. */
    public static final String MOTION_STILL_CATEGORY = "motion_still";
    public static final int MOTION_STILL = 0x8A2020;

    /** Last death marker (category {@code last_death}); the server sends the same colour. */
    public static final String LAST_DEATH_CATEGORY = "last_death";
    public static final int LAST_DEATH = 0xE040E0;

    /** Fallback when a blip has no colour (0). */
    public static final int BLIP_DEFAULT = 0x7CFC00;

    private RadarColors() {}
}
