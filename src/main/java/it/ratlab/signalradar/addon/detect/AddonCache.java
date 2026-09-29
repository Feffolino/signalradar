// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Per-player, per-addon detection results, reused until the addon's refresh interval has passed. */
public final class AddonCache {
    public static final AddonCache INSTANCE = new AddonCache();

    private record Entry(long time, int radius, String dimension, Vec3 pos, List<Hit> hits) {}

    private final Map<UUID, Map<ResourceLocation, Entry>> cache = new HashMap<>();

    /**
     * @param pos         where the player was when the hits were computed
     * @param incomplete  asked after {@code compute}: the result may be truncated (block-check budget ran out) and is
     *                    returned but not cached
     * @return the cached hits when younger than {@code ttlTicks}, computed for the same radius and dimension and the
     *         player has not moved more than {@code radius / 4} blocks since; otherwise {@code compute} is run
     */
    public List<Hit> get(UUID player, ResourceLocation addon, long now, long ttlTicks, int radius, String dimension, Vec3 pos,
                         Supplier<List<Hit>> compute, BooleanSupplier incomplete) {
        Map<ResourceLocation, Entry> m = cache.computeIfAbsent(player, k -> new HashMap<>());
        Entry e = m.get(addon);
        if (e != null && e.radius == radius && e.dimension.equals(dimension) && now >= e.time && now - e.time < ttlTicks
                && !movedTooFar(e.pos, pos, radius)) {
            return e.hits;
        }
        List<Hit> hits = List.copyOf(compute.get());
        if (incomplete.getAsBoolean()) {
            m.remove(addon);
        } else {
            m.put(addon, new Entry(now, radius, dimension, pos, hits));
        }
        return hits;
    }

    /** More than a quarter of the radius away from where the cached result was computed. */
    static boolean movedTooFar(Vec3 from, Vec3 to, int radius) {
        double limit = radius / 4.0;
        return from.distanceToSqr(to) > limit * limit;
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public void clear() {
        cache.clear();
    }
}
