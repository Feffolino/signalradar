// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.SignalRadarConfig;
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

/** One scan of one radar for one player. No world access: target positions come from the injected locator function. */
public final class RadarScanner {
    private RadarScanner() {}

    /**
     * Charges {@code scanCost} from the radar. If it cannot pay, nothing is charged and a NO SIGNAL snapshot (no blips)
     * is returned. Otherwise every target with {@code minTier <= tier} that the locator can place becomes a blip:
     * out-of-range targets are included (flagged), fuzzed with the maximum magnitude, names hidden past
     * their reveal distance.
     */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate) {
        int tier = RadarItem.tier(radar);
        int capacity = SignalRadarConfig.capacity();
        int energy = RadarItem.energy(radar);
        if (energy < settings.scanCost()) {
            return new ScanSnapshot(tier, energy, capacity, settings.range(tier), settings.refreshSeconds(), true, now, List.of());
        }
        RadarItem.setEnergy(radar, energy - settings.scanCost());
        energy = RadarItem.energy(radar);

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
        return new ScanSnapshot(tier, energy, capacity, settings.range(tier), settings.refreshSeconds(), false, now, blips);
    }
}
