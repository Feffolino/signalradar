// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import net.minecraft.client.Minecraft;

/**
 * Monotonic client-side tick counter for the sweep animation and its sounds.
 * {@code level.getGameTime()} is not usable: the server's time packets overwrite it about once a second, so any
 * client/server tick-rate drift makes it jump back or forward (the sweep stuttered and re-crossed blips).
 */
final class RadarClock {
    private static long ticks;

    private RadarClock() {}

    /** Client tick (post): advances only while a world is loaded and the game isn't paused. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            ticks++;
        }
    }

    static long ticks() {
        return ticks;
    }

    /** Fractional ticks for rendering. */
    static double ticks(float partial) {
        return ticks + partial;
    }

    static void reset() {
        ticks = 0;
    }
}
