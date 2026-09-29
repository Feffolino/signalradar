// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure addon helpers (no Minecraft classes, unit tested). */
public final class AddonMath {
    public static final int MAX_TIER = 4;
    /** Radius (also the config maximum) that stands for "the whole dimension" (team addon). */
    public static final int WHOLE_DIMENSION_RADIUS = 100_000;

    private AddonMath() {}

    /**
     * Radius grows linearly from {@code radiusMin} at {@code minTier} to {@code radiusMax} at tier 4. Below the min
     * tier the minimum is returned (such an addon is never active anyway).
     */
    public static int radius(int radiusMin, int radiusMax, int minTier, int tier) {
        if (tier >= MAX_TIER) {
            return radiusMax;
        }
        if (tier <= minTier) {
            return radiusMin;
        }
        double t = Math.min(1.0, (tier - minTier) / (double) (MAX_TIER - minTier));
        return (int) Math.round(radiusMin + (radiusMax - radiusMin) * t);
    }

    /**
     * Greedy vein merge. {@code points} are {@code {x, y, z, group}} ordered nearest first; a point is dropped when an
     * already kept point of the same group lies within {@code maxDist} blocks (euclidean).
     *
     * @return indices of the kept points, in input order
     */
    public static List<Integer> mergeClose(List<int[]> points, double maxDist) {
        double max2 = maxDist * maxDist;
        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            boolean merged = false;
            for (int k : kept) {
                int[] q = points.get(k);
                if (q[3] != p[3]) {
                    continue;
                }
                double dx = p[0] - q[0];
                double dy = p[1] - q[1];
                double dz = p[2] - q[2];
                if (dx * dx + dy * dy + dz * dz <= max2) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kept.add(i);
            }
        }
        return kept;
    }

    /** {@code #RRGGBB} or {@code RRGGBB} to an int, or {@code fallback} when malformed. */
    public static int parseColor(String s, int fallback) {
        if (s == null) {
            return fallback;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String formatColor(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** {@code village_plains} / {@code ns/ancient_city} to {@code Village Plains} / {@code Ns Ancient City}. */
    public static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_/\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    /** Ticks between motion tracker beeps: 4 when on top of you, 40 at {@code reference} blocks or more. */
    public static int motionBeepTicks(double dist, double reference) {
        double f = reference <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, dist / reference));
        return (int) Math.round(4 + 36 * f);
    }
}
