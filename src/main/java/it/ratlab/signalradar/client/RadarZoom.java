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
 * display shows. Remembered for the session (static); 0 = follow the tier range.
 */
final class RadarZoom {
    static final KeyMapping ZOOM_IN = new KeyMapping("key.signalradar.zoom_in", -1, "key.categories.signalradar");
    static final KeyMapping ZOOM_OUT = new KeyMapping("key.signalradar.zoom_out", -1, "key.categories.signalradar");

    private static int chosen;

    private RadarZoom() {}

    /** Range to display for a tier range {@code cap}; forgets a choice the tier can no longer reach. */
    static int range(int cap) {
        if (chosen > cap) {
            chosen = 0;
        }
        return RadarMath.effectiveRange(chosen, cap);
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
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
    }

    /** Key mappings: work while holding a radar in either hand. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        boolean holding = p != null && mc.screen == null && (p.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof RadarItem
                || p.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof RadarItem);
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

    static void reset() {
        chosen = 0;
    }
}
