// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ratlab.signalradar.addon.detect.MotionTracker;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddonMathTest {
    @Test
    void stationaryHostilesFromConfiguredTier() {
        assertTrue(!AddonMath.showsStationary(2, 3));
        assertTrue(AddonMath.showsStationary(3, 3));
        assertTrue(AddonMath.showsStationary(4, 3));
        assertTrue(AddonMath.showsStationary(0, 0));
        assertTrue(!AddonMath.showsStationary(4, AddonMath.STATIONARY_NEVER));
    }

    @Test
    void radiusIsLinearFromMinTierToTierFour() {
        // container: tier 0, 24 -> 48
        assertEquals(24, AddonMath.radius(24, 48, 0, 0));
        assertEquals(30, AddonMath.radius(24, 48, 0, 1));
        assertEquals(36, AddonMath.radius(24, 48, 0, 2));
        assertEquals(42, AddonMath.radius(24, 48, 0, 3));
        assertEquals(48, AddonMath.radius(24, 48, 0, 4));
        // ore: tier 1, 16 -> 32 (tier 1 = min, tier 4 = max)
        assertEquals(16, AddonMath.radius(16, 32, 1, 1));
        assertEquals(32, AddonMath.radius(16, 32, 1, 4));
        assertEquals(21, AddonMath.radius(16, 32, 1, 2));
        // below the min tier and min tier 4
        assertEquals(16, AddonMath.radius(16, 32, 2, 0));
        assertEquals(50, AddonMath.radius(10, 50, 4, 4));
    }

    @Test
    void mergeCloseKeepsOnePointPerVein() {
        // nearest first: two touching coal blocks, a far one, an iron block right next to the coal (other group)
        List<int[]> pts = List.of(new int[] {0, 0, 0, 1}, new int[] {1, 0, 1, 1}, new int[] {10, 0, 0, 1}, new int[] {1, 0, 0, 2},
                new int[] {2, 0, 0, 1}, new int[] {3, 0, 0, 1});
        assertEquals(List.of(0, 2, 3, 5), AddonMath.mergeClose(pts, 2.0));
        assertEquals(List.of(), AddonMath.mergeClose(List.of(), 2.0));
    }

    @Test
    void colourParsing() {
        assertEquals(0x7CFC00, AddonMath.parseColor("#7CFC00", -1));
        assertEquals(0x7CFC00, AddonMath.parseColor("7cfc00", -1));
        assertEquals(-1, AddonMath.parseColor("red", -1));
        assertEquals(-1, AddonMath.parseColor("#12345", -1));
        assertEquals(-1, AddonMath.parseColor(null, -1));
        assertEquals("#00FF10", AddonMath.formatColor(0x00FF10));
    }

    @Test
    void prettifyNames() {
        assertEquals("Village Plains", AddonMath.prettify("village_plains"));
        assertEquals("Ancient City", AddonMath.prettify("ancient_city"));
        assertEquals("A B", AddonMath.prettify("a/b"));
    }

    @Test
    void beepRateRisesWithProximity() {
        assertEquals(4, AddonMath.motionBeepTicks(0, 48));
        assertEquals(40, AddonMath.motionBeepTicks(48, 48));
        assertEquals(40, AddonMath.motionBeepTicks(500, 48));
        assertTrue(AddonMath.motionBeepTicks(10, 48) < AddonMath.motionBeepTicks(30, 48));
    }

    @Test
    void motionTrackerReportsOnlyEntitiesThatMoved() {
        MotionTracker t = new MotionTracker();
        UUID player = UUID.randomUUID();
        UUID still = UUID.randomUUID();
        UUID walker = UUID.randomUUID();
        // first sighting: nothing has a previous position
        assertEquals(Set.of(), t.update(player, List.of(new MotionTracker.Sample(still, 0, 64, 0), new MotionTracker.Sample(walker, 5, 64, 5)), 0.1));
        // second sample: the walker moved 2 blocks, the other 0.05
        Set<UUID> moving = t.update(player, List.of(new MotionTracker.Sample(still, 0.05, 64, 0), new MotionTracker.Sample(walker, 7, 64, 5)), 0.1);
        assertEquals(Set.of(walker), moving);
        // a different player has its own history
        assertEquals(Set.of(), t.update(UUID.randomUUID(), List.of(new MotionTracker.Sample(walker, 100, 64, 100)), 0.1));
        // an entity that left range and comes back counts as new (no stale previous position)
        t.update(player, List.of(), 0.1);
        assertEquals(Set.of(), t.update(player, List.of(new MotionTracker.Sample(walker, 50, 64, 50)), 0.1));
        t.forget(player);
        assertEquals(Set.of(), t.update(player, List.of(new MotionTracker.Sample(walker, 60, 64, 50)), 0.1));
    }
}
