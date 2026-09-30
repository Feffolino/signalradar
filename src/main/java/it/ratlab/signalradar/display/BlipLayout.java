// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure helper for drawing many blips without flicker: merges blips that sit almost on the same spot into one
 * (with a count) and gives every drawn blip a fixed depth slot. Everything here is deterministic and independent of the
 * frame: same input, same order, same slots.
 */
public final class BlipLayout {
    /** Blips closer than this fraction of the icon size (screen distance) are merged into one icon with a count badge. */
    public static final double MERGE_FRACTION = 0.35;
    /** Parts of one depth slot (arrow, backing, content, frame, marks, spare): see {@link #subStep}. */
    public static final int SUBLAYERS = 7;

    private BlipLayout() {}

    /**
     * @param index  position in the caller's blip list
     * @param x      display position (model units)
     * @param y      display position
     * @param found  already found (shown less important)
     * @param distSq squared distance from the player, importance tiebreak (nearer wins)
     * @param id     stable blip id, final tiebreak
     */
    public record Item(int index, double x, double y, boolean found, double distSq, String id) {}

    /** One drawn blip: {@code lead} is the shown one, {@code count} how many blips it stands for (at least 1). */
    public record Group(Item lead, int count) {}

    /** Importance order: not found first, then nearest, then id, then index. Total and stable. */
    public static final Comparator<Item> IMPORTANCE = Comparator
            .comparing(Item::found)
            .thenComparingDouble(Item::distSq)
            .thenComparing(Item::id)
            .thenComparingInt(Item::index);

    /** Draw order (and depth slot order): by id, then index. Independent of position and distance, so it never flips. */
    public static final Comparator<Group> DRAW_ORDER = Comparator
            .comparing((Group g) -> g.lead().id())
            .thenComparingInt(g -> g.lead().index());

    /**
     * Greedy grouping: walking in importance order, an unassigned blip becomes the lead of a group that swallows every
     * still unassigned blip closer than {@code threshold} to the lead. The result is sorted by {@link #DRAW_ORDER}.
     */
    public static List<Group> group(List<Item> items, double threshold) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(IMPORTANCE);
        int n = sorted.size();
        boolean[] taken = new boolean[n];
        double t2 = threshold * threshold;
        List<Group> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (taken[i]) {
                continue;
            }
            Item lead = sorted.get(i);
            int count = 1;
            if (threshold > 0) {
                for (int j = i + 1; j < n; j++) {
                    if (taken[j]) {
                        continue;
                    }
                    Item o = sorted.get(j);
                    double dx = o.x() - lead.x();
                    double dy = o.y() - lead.y();
                    if (dx * dx + dy * dy < t2) {
                        taken[j] = true;
                        count++;
                    }
                }
            }
            out.add(new Group(lead, count));
        }
        out.sort(DRAW_ORDER);
        return out;
    }

    /**
     * Depth distance between two consecutive blip slots: {@code maxStep} unless {@code slots} of them would not fit in
     * {@code budget} model units.
     */
    public static double slotStep(int slots, double budget, double maxStep) {
        if (slots <= 0) {
            return maxStep;
        }
        return Math.min(maxStep, budget / (slots + 1));
    }

    /** Depth of one sub-layer inside a slot of the given step. */
    public static double subStep(double slotStep) {
        return slotStep / SUBLAYERS;
    }
}
