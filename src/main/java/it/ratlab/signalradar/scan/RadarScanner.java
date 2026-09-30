// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.icon.IconSpec;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.target.TargetDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * One scan of one radar for one player. No world access: target positions come from the injected locator function,
 * addon hits from the injected detect function.
 */
public final class RadarScanner {
    /** Whether a scan pays the base scan cost (+ addon costs) or rides on a payment made earlier in the same period. */
    public enum Charge { PAY, FREE }

    /** Blip id, category and colour of the last death marker (the client colour constant is {@code RadarColors.LAST_DEATH}). */
    public static final String LAST_DEATH_ID = "signalradar:last_death";
    public static final String LAST_DEATH_CATEGORY = "last_death";
    public static final int LAST_DEATH_COLOR = 0xE040E0;

    private RadarScanner() {}

    /** A target the player may see: unlocked when it needs an unlock, staged when it needs a stage, not hidden after being found. */
    public static boolean visible(TargetDef def, PlayerProgress progress) {
        if (def.requiresUnlock() && !progress.unlocked().test(def.id())) {
            return false;
        }
        if (def.requiresStage() != null && !progress.stage().test(def.requiresStage())) {
            return false;
        }
        return !(def.hideWhenFound() && progress.found().test(def.id()));
    }

    /** A charged scan with narrative targets only (no addons). */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate) {
        return scan(radar, player, playerPos, now, settings, defs, locate, List.of(), a -> List.of(), Charge.PAY);
    }

    /** As the full scan, with the player's progression left empty. */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate,
                                    List<AddonSettings> addons, Function<AddonSettings, List<Hit>> detect, Charge charge) {
        return scan(radar, player, playerPos, now, settings, defs, locate, addons, detect, charge, PlayerProgress.NONE);
    }

    /** The snapshot of a radar that cannot pay: no blips. Charges nothing. */
    public static ScanSnapshot noSignal(ItemStack radar, ScanSettings settings, long now, boolean charged) {
        int tier = RadarItem.tier(radar);
        return new ScanSnapshot(tier, RadarItem.energy(radar), SignalRadarConfig.capacity(), settings.range(tier), settings.refreshSeconds(),
                true, now, List.of(), charged);
    }

    /**
     * With {@link Charge#PAY}, charges {@code ceil((scanCost + sum of the addons' energyCost) * energyMultiplier[tier])}. If it cannot pay, nothing is
     * charged and a NO SIGNAL snapshot (no blips) is returned. Otherwise every target with {@code minTier <= tier} that
     * the locator can place becomes a blip (out-of-range targets are included, flagged, fuzzed with the maximum
     * magnitude, names hidden past their reveal distance), followed by the blips of the addons' detections
     * (fuzzed by tier, names always shown).
     *
     * <p>Targets are skipped (before locating) when they need an unlock or a stage the player lacks, or are found and
     * {@code hide_when_found}; found ones that stay are flagged. The last death of the player (same dimension) is
     * a blip of category {@code last_death} on every tier.
     *
     * @param addons the installed addons that count (see {@link AddonRules#active})
     * @param detect raw hits of one addon (cached by the caller)
     */
    public static ScanSnapshot scan(ItemStack radar, UUID player, Vec3 playerPos, long now, ScanSettings settings,
                                    Collection<TargetDef> defs, Function<TargetDef, Optional<Vec3>> locate,
                                    List<AddonSettings> addons, Function<AddonSettings, List<Hit>> detect, Charge charge,
                                    PlayerProgress progress) {
        int tier = RadarItem.tier(radar);
        int capacity = SignalRadarConfig.capacity();
        int energy = RadarItem.energy(radar);
        boolean paying = charge == Charge.PAY;
        if (paying) {
            long cost = settings.charge(tier, AddonRules.energyCost(addons));
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
            if (def.minTier() > tier || !visible(def, progress)) {
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
                    revealed ? def.name() : Blip.UNKNOWN_NAME, dist > range, progress.found().test(def.id()), def.icon()));
        }
        if (progress.lastDeath() != null) {
            Vec3 real = progress.lastDeath();
            double dist = ScanMath.distance(playerPos, real);
            Vec3 shown = ScanMath.fuzz(player, LAST_DEATH_ID, now, real, ScanMath.fuzzMagnitude(maxFuzz, dist, range));
            blips.add(new Blip(LAST_DEATH_ID, LAST_DEATH_CATEGORY, LAST_DEATH_COLOR, shown.x, shown.y, shown.z,
                    Component.translatable("blip.signalradar.last_death"), dist > range, false, IconSpec.LAST_DEATH));
        }
        for (AddonSettings addon : addons) {
            String category = addon.def().category();
            for (Hit hit : detect.apply(addon)) {
                Vec3 real = new Vec3(hit.x(), hit.y(), hit.z());
                double dist = ScanMath.distance(playerPos, real);
                String id = addon.def().id() + "/" + hit.key();
                Vec3 shown = ScanMath.fuzz(player, id, now, real, ScanMath.fuzzMagnitude(maxFuzz, dist, range));
                int color = hit.color() != 0 ? hit.color() : addon.color();
                String icon = addon.def().icon() != null ? addon.def().icon() : hit.icon();
                blips.add(new Blip(id, category, color, shown.x, shown.y, shown.z, hit.name(), dist > range, false, icon));
            }
        }
        int motionRadius = 0;
        for (AddonSettings a : addons) {
            if (a.def().detector() == it.ratlab.signalradar.addon.AddonDefinition.Detector.MOTION) {
                motionRadius = Math.max(motionRadius, a.radius(tier, settings.range(tier)));
            }
        }
        return new ScanSnapshot(tier, energy, capacity, settings.range(tier), settings.refreshSeconds(), false, now, blips, paying, motionRadius);
    }
}
