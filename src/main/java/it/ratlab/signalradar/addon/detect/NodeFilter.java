// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Mod-agnostic filtering of a network of fixed nodes (Manhole Travel): same dimension, within a horizontal radius, not a
 * home node, not already opened by the player. Nearest first, capped. Kept free of Minecraft and optional-mod classes so it
 * can be unit tested.
 */
public final class NodeFilter {
    /** A node as the filter sees it. {@code ref} carries the caller's own object back out. */
    public record Node<T>(T ref, String key, String dimension, double x, double y, double z, boolean home) {}

    private NodeFilter() {}

    /**
     * @param isOpen true for keys the player already has in his network (those are dropped; null = keep all)
     * @param max    maximum number of results
     */
    public static <T> List<Node<T>> select(Collection<Node<T>> nodes, String dimension, double px, double pz, int radius,
                                           Predicate<String> isOpen, int max) {
        return select(nodes, dimension, px, pz, radius, isOpen, false, max);
    }

    public static <T> List<Node<T>> select(Collection<Node<T>> nodes, String dimension, double px, double pz, int radius,
                                           Predicate<String> isOpen, boolean includeHome, int max) {
        double r2 = (double) radius * radius;
        List<Node<T>> out = new ArrayList<>();
        for (Node<T> n : nodes) {
            if ((!includeHome && n.home()) || !n.dimension().equals(dimension)) {
                continue;
            }
            double dx = n.x() - px;
            double dz = n.z() - pz;
            if (dx * dx + dz * dz > r2 || (isOpen != null && isOpen.test(n.key()))) {
                continue;
            }
            out.add(n);
        }
        out.sort(Comparator.comparingDouble((Node<T> n) -> {
            double dx = n.x() - px;
            double dz = n.z() - pz;
            return dx * dx + dz * dz;
        }).thenComparing(Node::key));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
