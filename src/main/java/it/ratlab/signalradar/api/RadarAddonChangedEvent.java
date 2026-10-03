// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the Forge event bus (server side) after an addon slot of a radar changed, already written to the radar:
 * from the addon GUI (one event per changed slot) and from {@code /signalradar addon} (slot = position in the installed
 * list; removing shifts the later addons down, only the removed slot is reported). Not cancellable.
 */
public class RadarAddonChangedEvent extends PlayerEvent {
    private final ItemStack radar;
    private final int slot;
    @Nullable
    private final ResourceLocation oldAddon;
    @Nullable
    private final ResourceLocation newAddon;

    public RadarAddonChangedEvent(ServerPlayer player, ItemStack radar, int slot, @Nullable ResourceLocation oldAddon,
                                  @Nullable ResourceLocation newAddon) {
        super(player);
        this.radar = radar;
        this.slot = slot;
        this.oldAddon = oldAddon;
        this.newAddon = newAddon;
    }

    public ServerPlayer getPlayer() {
        return (ServerPlayer) getEntity();
    }

    public ItemStack getRadar() {
        return radar;
    }

    /** Slot index, 0 based. */
    public int getSlot() {
        return slot;
    }

    /** Addon id that was in the slot, null when it was empty. */
    @Nullable
    public ResourceLocation getOldAddon() {
        return oldAddon;
    }

    /** Addon id now in the slot, null when it is empty now. */
    @Nullable
    public ResourceLocation getNewAddon() {
        return newAddon;
    }
}
