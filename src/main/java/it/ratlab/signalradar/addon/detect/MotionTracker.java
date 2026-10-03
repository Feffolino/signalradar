// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers where each entity was at the previous motion sample of each player. An entity "moves" when it is more than
 * {@code minMove} blocks from its previous sample; first sightings do not count. No Minecraft classes (unit tested).
 */
public final class MotionTracker {
    public static final MotionTracker INSTANCE = new MotionTracker();

    public record Sample(UUID id, double x, double y, double z) {}

    private final Map<UUID, Map<UUID, double[]>> last = new HashMap<>();

    /**
     * Compares {@code samples} with the player's previous sample, stores them as the new previous sample (entities
     * missing from {@code samples} are forgotten) and returns the ids that moved.
     */
    public Set<UUID> update(UUID player, Collection<Sample> samples, double minMove) {
        Map<UUID, double[]> prev = last.getOrDefault(player, Map.of());
        Map<UUID, double[]> next = new HashMap<>();
        Set<UUID> moving = new HashSet<>();
        double min2 = minMove * minMove;
        for (Sample s : samples) {
            double[] p = prev.get(s.id());
            if (p != null) {
                double dx = s.x() - p[0];
                double dy = s.y() - p[1];
                double dz = s.z() - p[2];
                if (dx * dx + dy * dy + dz * dz > min2) {
                    moving.add(s.id());
                }
            }
            next.put(s.id(), new double[] {s.x(), s.y(), s.z()});
        }
        last.put(player, next);
        return moving;
    }

    public void forget(UUID player) {
        last.remove(player);
    }

    public void clear() {
        last.clear();
    }
}
