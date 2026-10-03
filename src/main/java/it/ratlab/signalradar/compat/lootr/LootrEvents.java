// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.lootr;

import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.detect.AddonCache;
import it.ratlab.signalradar.scan.ScanHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import noobanidus.mods.lootr.api.inventory.ILootrInventory;

/**
 * Drops the Lootr addon's cached result of a player who opens (or closes) a Lootr container, so the chest disappears from the
 * radar at the next snapshot instead of after the addon refresh. Only class-loaded after a {@code ModList} check.
 */
public final class LootrEvents {
    private LootrEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(LootrEvents::onOpen);
        bus.addListener(LootrEvents::onClose);
    }

    private static void onOpen(PlayerContainerEvent.Open event) {
        handle(event.getEntity(), event.getContainer());
    }

    private static void onClose(PlayerContainerEvent.Close event) {
        handle(event.getEntity(), event.getContainer());
    }

    private static void handle(net.minecraft.world.entity.player.Player p, AbstractContainerMenu menu) {
        if (p instanceof ServerPlayer player && isLootrMenu(menu)) {
            refresh(player);
        }
    }

    /** True when one of the menu's container slots is a Lootr inventory. */
    static boolean isLootrMenu(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof ILootrInventory) {
                return true;
            }
        }
        return false;
    }

    /** Invalidates the player's Lootr addon cache entry and asks for a free snapshot soon. */
    public static void refresh(ServerPlayer player) {
        AddonCache.INSTANCE.invalidate(player.getUUID(), AddonRegistry.LOOTR);
        ScanHandler.requestRefresh(player);
    }
}
