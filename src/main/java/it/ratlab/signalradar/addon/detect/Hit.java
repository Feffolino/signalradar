// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import net.minecraft.network.chat.Component;

/**
 * One raw detection of an addon (real position, before fuzz).
 *
 * @param key   stable per target (block position / entity uuid / structure id), used for the blip id
 * @param name  block / entity / structure name (addon blips are always revealed)
 * @param color 0xRRGGBB, 0 = use the addon colour
 */
public record Hit(String key, Component name, double x, double y, double z, int color) {}
