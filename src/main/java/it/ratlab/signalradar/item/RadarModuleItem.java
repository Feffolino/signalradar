// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import net.minecraft.world.item.Item;

/** Upgrade module: crafting it with a radar of tier {@code tier - 1} gives a radar of this tier. */
public class RadarModuleItem extends Item {
    private final int tier;

    public RadarModuleItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }
}
