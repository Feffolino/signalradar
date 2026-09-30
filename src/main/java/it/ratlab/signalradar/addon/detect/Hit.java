// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import net.minecraft.network.chat.Component;

/**
 * One raw detection of an addon (real position, before fuzz).
 *
 * @param key   stable per target (block position / entity uuid / structure id), used for the blip id
 * @param name  block / entity / structure name (addon blips are always revealed)
 * @param color 0xRRGGBB, 0 = use the addon colour
 * @param icon  icon spec of the blip (see {@link it.ratlab.signalradar.icon.IconSpec}), empty = dot
 */
public record Hit(String key, Component name, double x, double y, double z, int color, String icon) {
    public Hit {
        icon = icon == null ? "" : icon;
    }

    /** A hit without an icon (drawn as a dot). */
    public Hit(String key, Component name, double x, double y, double z, int color) {
        this(key, name, x, y, z, color, "");
    }
}
