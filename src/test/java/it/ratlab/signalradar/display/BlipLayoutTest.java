// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ratlab.signalradar.display.BlipLayout.Group;
import it.ratlab.signalradar.display.BlipLayout.Item;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BlipLayoutTest {
    private static Item item(int i, String id, double x, double y, boolean found, double d) {
        return new Item(i, x, y, found, d, id);
    }

    @Test
    void distantBlipsStaySeparate() {
        List<Group> g = BlipLayout.group(List.of(item(0, "a", 0, 0, false, 1), item(1, "b", 1, 0, false, 2)), 0.3);
        assertEquals(2, g.size());
        assertEquals(1, g.get(0).count());
    }

    @Test
    void closeBlipsMergeUnderTheNearest() {
        List<Group> g = BlipLayout.group(List.of(
                item(0, "far", 0.1, 0, false, 900), item(1, "near", 0, 0, false, 100), item(2, "other", 5, 5, false, 400)), 0.3);
        assertEquals(2, g.size());
        Group merged = g.stream().filter(x -> x.count() == 2).findFirst().orElseThrow();
        assertEquals("near", merged.lead().id());
    }

    @Test
    void foundYieldsToNotFound() {
        List<Group> g = BlipLayout.group(List.of(item(0, "a", 0, 0, true, 1), item(1, "b", 0.05, 0, false, 50)), 0.3);
        assertEquals(1, g.size());
        assertEquals("b", g.get(0).lead().id());
        assertEquals(2, g.get(0).count());
    }

    @Test
    void thresholdIsExclusiveAndZeroDisablesMerging() {
        assertEquals(2, BlipLayout.group(List.of(item(0, "a", 0, 0, false, 1), item(1, "b", 0.3, 0, false, 2)), 0.3).size());
        assertEquals(2, BlipLayout.group(List.of(item(0, "a", 0, 0, false, 1), item(1, "b", 0, 0, false, 2)), 0).size());
    }

    @Test
    void identicalPositionsMergeAll() {
        List<Item> l = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            l.add(item(i, "id" + i, 2, 2, false, 10));
        }
        List<Group> g = BlipLayout.group(l, 0.3);
        assertEquals(1, g.size());
        assertEquals(5, g.get(0).count());
        assertEquals("id0", g.get(0).lead().id());
    }

    @Test
    void orderIsIndependentOfInputOrderAndMovement() {
        Random rnd = new Random(7);
        List<Item> base = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            base.add(item(i, "t" + i, rnd.nextInt(6), rnd.nextInt(6), i % 5 == 0, rnd.nextInt(3)));
        }
        List<Item> shuffled = new ArrayList<>(base);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(shuffled, rnd);
            List<Group> a = BlipLayout.group(base, 0.3);
            List<Group> b = BlipLayout.group(shuffled, 0.3);
            assertEquals(a.stream().map(x -> x.lead().index()).toList(), b.stream().map(x -> x.lead().index()).toList());
        }
        // Distances changing (player moving) never reorder the drawing slots of non-merged blips.
        List<Item> moved = new ArrayList<>();
        for (Item it : base) {
            moved.add(new Item(it.index(), it.x(), it.y(), it.found(), it.distSq() * 7 + 3, it.id()));
        }
        List<Integer> a = BlipLayout.group(base, 0).stream().map(x -> x.lead().index()).toList();
        List<Integer> b = BlipLayout.group(moved, 0).stream().map(x -> x.lead().index()).toList();
        assertEquals(a, b);
    }

    @Test
    void drawOrderIsById() {
        List<Group> g = BlipLayout.group(List.of(item(0, "z", 0, 0, false, 1), item(1, "a", 3, 0, false, 9)), 0.3);
        assertEquals("a", g.get(0).lead().id());
        assertEquals("z", g.get(1).lead().id());
    }

    @Test
    void slotStepFitsTheBudget() {
        assertEquals(0.002, BlipLayout.slotStep(10, 0.085, 0.002), 1e-12);
        double s = BlipLayout.slotStep(256, 0.085, 0.002);
        assertTrue(s * 257 <= 0.085 + 1e-12);
        assertTrue(BlipLayout.subStep(s) > 0);
        assertEquals(0.002, BlipLayout.slotStep(0, 0.085, 0.002), 1e-12);
    }
}
