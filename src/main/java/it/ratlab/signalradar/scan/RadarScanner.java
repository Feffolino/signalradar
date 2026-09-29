// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.target.TargetDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * One scan of one radar for one player. No world access: target positions come from the injected locator function,
 * addon hits from the injected detect function.
 */
public final class RadarScanner {
    /** Whether a scan pays the base scan cost (+ addon costs) or rides on a payment made earlier in the same period. */
    public enum Charge { PAY, FREE }

    private RadarScanner() {}

    /** A charged scan with narrative targets only (no addons). */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate) {
        return scan(radar, player, playerPos, now, settings, defs, locate, List.of(), a -> List.of(), Charge.PAY);
    }

    /** The snapshot of a radar that cannot pay: no blips. Charges nothing. */
    public static ScanSnapshot noSignal(ItemStack radar, ScanSettings settings, long now, boolean charged) {
        int tier = RadarItem.tier(radar);
        return new ScanSnapshot(tier, RadarItem.energy(radar), SignalRadarConfig.capacity(), settings.range(tier), settings.refreshSeconds(),
                true, now, List.of(), charged);
    }

    /**
     * With {@link Charge#PAY}, charges {@code scanCost + sum of the addons' energyCost}. If it cannot pay, nothing is
     * charged and a NO SIGNAL snapshot (no blips) is returned. Otherwise every target with {@code minTier <= tier} that
     * the locator can place becomes a blip (out-of-range targets are included, flagged, fuzzed with the maximum
     * magnitude, names hidden past their reveal distance), followed by the blips of the addons' detections
     * (fuzzed by tier, names always shown).
     *
     * @param addons the installed addons that count (see {@link AddonRules#active})
     * @param detect raw hits of one addon (cached by the caller)
     */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate,
                                    List<AddonSettings> addons, Function<AddonSettings, List<Hit>> detect, Charge charge) {
        int tier = RadarItem.tier(radar);
        int capacity = SignalRadarConfig.capacity();
        int energy = RadarItem.energy(radar);
        boolean paying = charge == Charge.PAY;
        if (paying) {
            long cost = (long) settings.scanCost() + AddonRules.energyCost(addons);
            if (energy < cost) {
                return noSignal(radar, settings, now, true);
            }
            RadarItem.setEnergy(radar, (int) (energy - cost));
            energy = RadarItem.energy(radar);
        }

        double range = settings.range(tier);
        int maxFuzz = settings.fuzz(tier);
        List<TargetDef> sorted = new ArrayList<>(defs);
        sorted.sort(Comparator.comparing(d -> d.id().toString()));
        List<Blip> blips = new ArrayList<>();
        for (TargetDef def : sorted) {
            if (def.minTier() > tier) {
                continue;
            }
            Optional<Vec3> located = locate.apply(def);
            if (located.isEmpty()) {
                continue;
            }
            Vec3 real = located.get();
            double dist = ScanMath.distance(playerPos, real);
            Vec3 shown = ScanMath.fuzz(player, def.blipId(), now, real, ScanMath.fuzzMagnitude(maxFuzz, dist, range));
            boolean revealed = ScanMath.revealed(dist, def.revealDistance());
            blips.add(new Blip(def.blipId(), def.category(), def.color(), shown.x, shown.y, shown.z,
                    revealed ? def.name() : Blip.UNKNOWN_NAME, dist > range, false));
        }
        for (AddonSettings addon : addons) {
            String category = addon.def().category();
            for (Hit hit : detect.apply(addon)) {
                Vec3 real = new Vec3(hit.x(), hit.y(), hit.z());
                double dist = ScanMath.distance(playerPos, real);
                String id = addon.def().id() + "/" + hit.key();
                Vec3 shown = ScanMath.fuzz(player, id, now, real, ScanMath.fuzzMagnitude(maxFuzz, dist, range));
                int color = hit.color() != 0 ? hit.color() : addon.color();
                blips.add(new Blip(id, category, color, shown.x, shown.y, shown.z, hit.name(), dist > range, false));
            }
        }
        return new ScanSnapshot(tier, energy, capacity, settings.range(tier), settings.refreshSeconds(), false, now, blips, paying);
    }
}
