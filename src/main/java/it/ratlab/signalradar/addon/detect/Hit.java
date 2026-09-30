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
 * @param category optional blip category overriding the addon category, null = the addon category
 */
public record Hit(String key, Component name, double x, double y, double z, int color, String icon, String category) {
    public Hit {
        icon = icon == null ? "" : icon;
    }

    /** A hit in the addon category. */
    public Hit(String key, Component name, double x, double y, double z, int color, String icon) {
        this(key, name, x, y, z, color, icon, null);
    }

    /** A hit without an icon (drawn as a dot). */
    public Hit(String key, Component name, double x, double y, double z, int color) {
        this(key, name, x, y, z, color, "");
    }
}
