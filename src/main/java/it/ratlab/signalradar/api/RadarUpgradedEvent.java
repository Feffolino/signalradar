// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge event bus (server side) when a player takes the result of the radar upgrade recipe (radar +
 * tier module) out of a crafting grid. Not cancellable. Shift-click crafting posts it once per crafted radar.
 */
public class RadarUpgradedEvent extends PlayerEvent {
    private final ItemStack radar;
    private final int oldTier;
    private final int newTier;

    public RadarUpgradedEvent(ServerPlayer player, ItemStack radar, int oldTier, int newTier) {
        super(player);
        this.radar = radar;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    public ServerPlayer getPlayer() {
        return (ServerPlayer) getEntity();
    }

    /** The crafted radar. */
    public ItemStack getRadar() {
        return radar;
    }

    public int getOldTier() {
        return oldTier;
    }

    public int getNewTier() {
        return newTier;
    }
}
