// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.display.RadarMath;
import it.ratlab.signalradar.display.Vec2;
import it.ratlab.signalradar.display.Vec3d;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModSounds;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Local radar sounds: a ping when a new scan arrives while a radar is held, and a tick when the sweep crosses a
 * {@code narrative} blip (checked once per client tick, not per frame; other categories never tick). Phase 4 adds the
 * motion tracker's blips and beep here.
 */
public final class RadarClientSounds {
    public static final String TICKING_CATEGORY = "narrative";

    private RadarClientSounds() {}

    /** The radar held by the local player (main hand first), or empty. */
    public static ItemStack held(LocalPlayer p) {
        if (p.getMainHandItem().getItem() instanceof RadarItem) {
            return p.getMainHandItem();
        }
        return p.getOffhandItem().getItem() instanceof RadarItem ? p.getOffhandItem() : ItemStack.EMPTY;
    }

    static void onSnapshot(ScanSnapshot snap) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && !snap.noSignal() && !held(p).isEmpty()) {
            play(p, ModSounds.SCAN_PING.get(), 1f, 1f);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.isPaused()) {
            return;
        }
        ItemStack radar = held(p);
        ScanSnapshot snap = ClientRadarState.latest();
        if (radar.isEmpty() || snap == null || snap.noSignal() || RadarItem.energy(radar) <= 0) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (RadarMath.stale(nowMs, ClientRadarState.receivedAtMillis(), snap.refreshSeconds())) {
            return;
        }
        long t = mc.level.getGameTime();
        double prev = RadarMath.sweepAngle(t - 1);
        double cur = RadarMath.sweepAngle(t);
        for (Blip b : snap.blips()) {
            if (!TICKING_CATEGORY.equals(b.category())) {
                continue;
            }
            Vec3d wp = ClientRadarState.position(b, nowMs);
            Vec2 rel = RadarMath.relative(wp.x() - p.getX(), wp.z() - p.getZ(), p.getViewYRot(1f));
            if (RadarMath.sweepCrossed(prev, cur, RadarMath.displayAngle(rel.x(), rel.y()))) {
                play(p, ModSounds.BLIP.get(), 1f, b.outOfRange() ? 0.8f : 1f);
                return; // one tick per client tick is plenty
            }
        }
    }

    private static void play(LocalPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
