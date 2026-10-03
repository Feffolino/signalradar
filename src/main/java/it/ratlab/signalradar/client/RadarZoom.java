// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.display.RadarMath;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.scan.ScanSnapshot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;

/**
 * Client-side display range (zoom). The server still scans the whole tier range; this only changes how much of it the
 * display shows. Saved in the client config ({@code zoomRange}) so it survives restarts; 0 = follow the tier range.
 */
final class RadarZoom {
    static final KeyMapping ZOOM_IN = new KeyMapping("key.signalradar.zoom_in", -1, "key.categories.signalradar");
    static final KeyMapping ZOOM_OUT = new KeyMapping("key.signalradar.zoom_out", -1, "key.categories.signalradar");

    private static int chosen = -1; // -1 = not read from the client config yet
    /** Wall-clock ms of the last unsaved change, 0 = nothing to save. */
    private static long dirtySince;
    /** Save this long after the last zoom step (a crash then loses at most a few seconds of zooming). */
    private static final long SAVE_DELAY_MS = 3000;

    private RadarZoom() {}

    /** Range to display for a tier range {@code cap}; forgets a choice the tier can no longer reach. */
    static int range(int cap) {
        // A choice above this tier's cap only shows the cap; the saved choice is kept for stronger radars.
        return RadarMath.effectiveRange(chosen(), cap);
    }

    /** One step: +1 larger range (zoom out), -1 smaller (zoom in). Needs a snapshot (its range is the tier cap). */
    static void step(int dir) {
        ScanSnapshot snap = ClientRadarState.latest();
        if (snap == null || snap.range() <= 0) {
            return;
        }
        int cap = snap.range();
        int current = range(cap);
        int next = RadarMath.stepRange(current, cap, dir);
        if (next == current) {
            return;
        }
        chosen = next >= cap ? 0 : next;
        dirtySince = System.currentTimeMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.4f, 0.35f));
    }

    /** Key mappings: work while holding a radar in either hand. */
    static void tick() {
        if (dirtySince != 0 && System.currentTimeMillis() - dirtySince >= SAVE_DELAY_MS) {
            flush();
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean holding = p != null && mc.screen == null && (p.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof RadarItem
                || p.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof RadarItem && !ClientTwoHanded.blocksOffhand(p));
        while (ZOOM_IN.consumeClick()) {
            if (holding) {
                step(-1);
            }
        }
        while (ZOOM_OUT.consumeClick()) {
            if (holding) {
                step(1);
            }
        }
    }

    private static int chosen() {
        if (chosen < 0) {
            chosen = RadarClientConfig.zoomRange();
        }
        return chosen;
    }

    /** Writes a pending change to the client config. Also called on logout and on game shutdown. */
    static void flush() {
        if (dirtySince != 0 && chosen >= 0) {
            RadarClientConfig.saveZoomRange(chosen);
        }
        dirtySince = 0;
    }

    /** Logout / world change: save, then re-read the saved choice next time (the file may be edited meanwhile). */
    static void reset() {
        flush();
        chosen = -1;
    }
}
