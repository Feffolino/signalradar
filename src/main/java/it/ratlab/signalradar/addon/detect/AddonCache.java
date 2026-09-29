// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/** Per-player, per-addon detection results, reused until the addon's refresh interval has passed. */
public final class AddonCache {
    public static final AddonCache INSTANCE = new AddonCache();

    private record Entry(long time, int radius, String dimension, List<Hit> hits) {}

    private final Map<UUID, Map<ResourceLocation, Entry>> cache = new HashMap<>();

    /**
     * @return the cached hits when younger than {@code ttlTicks} and computed for the same radius and dimension,
     *         otherwise {@code compute} is run and its result stored
     */
    public List<Hit> get(UUID player, ResourceLocation addon, long now, long ttlTicks, int radius, String dimension,
                         Supplier<List<Hit>> compute) {
        Map<ResourceLocation, Entry> m = cache.computeIfAbsent(player, k -> new HashMap<>());
        Entry e = m.get(addon);
        if (e != null && e.radius == radius && e.dimension.equals(dimension) && now >= e.time && now - e.time < ttlTicks) {
            return e.hits;
        }
        List<Hit> hits = List.copyOf(compute.get());
        m.put(addon, new Entry(now, radius, dimension, hits));
        return hits;
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public void clear() {
        cache.clear();
    }
}
