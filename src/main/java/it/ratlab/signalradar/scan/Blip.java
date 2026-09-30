// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import net.minecraft.network.chat.Component;

/**
 * One blip as the client sees it (positions already fuzzed by the server).
 *
 * @param id         stable id (target id), the client uses it to glide blips between snapshots
 * @param category   free text category ("narrative", ...)
 * @param color      0xRRGGBB
 * @param x          fuzzed world position
 * @param name       display name, or {@code ???} while the target is farther than its reveal distance
 * @param outOfRange farther than the radar's range: draw on the rim with an arrow
 * @param found      already found by this player
 * @param icon       icon spec (see {@link it.ratlab.signalradar.icon.IconSpec}), empty = coloured dot
 */
public record Blip(String id, String category, int color, double x, double y, double z, Component name,
                   boolean outOfRange, boolean found, String icon) {
    public static final Component UNKNOWN_NAME = Component.literal("???");

    public Blip {
        icon = icon == null ? "" : icon;
    }
}
