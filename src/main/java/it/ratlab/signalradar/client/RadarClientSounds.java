// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.addon.AddonMath;
import it.ratlab.signalradar.display.RadarMath;
import it.ratlab.signalradar.display.Vec2;
import it.ratlab.signalradar.display.Vec3d;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModSounds;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Local radar sounds: a ping when a new scan arrives while a radar is held, and a tick when the sweep crosses a
 * {@code narrative} or {@code motion} blip (checked once per client tick, not per frame; other categories never tick),
 * and the motion tracker beep.
 */
public final class RadarClientSounds {
    /** Only these categories tick when the sweep crosses them (never ore, containers, ...). */
    public static final Set<String> TICKING_CATEGORIES = Set.of("narrative", RadarColors.MOTION_CATEGORY);
    /** Beep reference distance when a snapshot does not say (motion addon radius at tier 4). */
    private static final double BEEP_REFERENCE_FALLBACK = 48.0;
    private static int beepCooldown;
    /** Last ping time (ms): at most one ping per ~80 % of the scan period, however many charged snapshots arrive. */
    private static long lastPingMs;
    /** Radar-clock tick of the last tick sound per blip id: a blip ticks at most once per sweep turn (turning the
     *  view can move a blip back across the sweep line). */
    private static final Map<String, Long> LAST_TICK = new HashMap<>();

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
        if (p != null && snap.charged() && !snap.noSignal() && !held(p).isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastPingMs >= Math.max(1, snap.refreshSeconds()) * 800L) {
                lastPingMs = now;
                play(p, ModSounds.SCAN_PING.get(), 1f, 1f);
            }
        }
    }

    static void reset() {
        lastPingMs = 0;
        beepCooldown = 0;
        LAST_TICK.clear();
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
            beepCooldown = 0;
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (RadarMath.stale(nowMs, ClientRadarState.receivedAtMillis(), snap.refreshSeconds())) {
            beepCooldown = 0;
            return;
        }
        motionBeep(p, snap, nowMs);
        long t = RadarClock.ticks();
        double prev = RadarMath.sweepAngle(t - 1);
        double cur = RadarMath.sweepAngle(t);
        for (Blip b : snap.blips()) {
            if (!TICKING_CATEGORIES.contains(b.category())) {
                continue;
            }
            Vec3d wp = ClientRadarState.position(b, nowMs);
            Vec2 rel = RadarMath.relative(wp.x() - p.getX(), wp.z() - p.getZ(), p.getViewYRot(1f));
            if (RadarMath.sweepCrossed(prev, cur, RadarMath.displayAngle(rel.x(), rel.y()))) {
                Long last = LAST_TICK.get(b.id());
                if (last != null && t - last < RadarMath.SWEEP_PERIOD_TICKS * 0.8) {
                    continue;
                }
                if (LAST_TICK.size() > 256) {
                    LAST_TICK.clear();
                }
                LAST_TICK.put(b.id(), t);
                play(p, ModSounds.BLIP.get(), 1f, b.outOfRange() ? 0.9f : 1f);
                return; // one tick per client tick is plenty
            }
        }
    }

    /** Alien-style beep of the nearest motion blip: the closer it is, the faster the beeps. */
    private static void motionBeep(LocalPlayer p, ScanSnapshot snap, long nowMs) {
        if (!RadarClientConfig.motionBeep()) {
            beepCooldown = 0;
            return;
        }
        double nearest = Double.MAX_VALUE;
        for (Blip b : snap.blips()) {
            if (RadarColors.MOTION_CATEGORY.equals(b.category())) {
                Vec3d wp = ClientRadarState.position(b, nowMs);
                double dx = wp.x() - p.getX();
                double dz = wp.z() - p.getZ();
                nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
            }
        }
        if (nearest == Double.MAX_VALUE) {
            beepCooldown = 0;
            return;
        }
        double reference = snap.motionRadius() > 0 ? snap.motionRadius() : BEEP_REFERENCE_FALLBACK;
        if (--beepCooldown <= 0) {
            float pitch = (float) (1.25 - 0.35 * Math.min(1.0, nearest / reference));
            play(p, ModSounds.MOTION_BEEP.get(), 0.7f, pitch);
            beepCooldown = AddonMath.motionBeepTicks(nearest, reference);
        }
    }

    private static void play(LocalPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
