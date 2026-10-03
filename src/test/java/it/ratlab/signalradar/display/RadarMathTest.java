// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class RadarMathTest {
    private static final double EPS = 1e-9;

    @Test
    void relativeFollowsYaw() {
        // Facing south (yaw 0): +z ahead, west (-x) to the right.
        assertVec(0, 10, RadarMath.relative(0, 10, 0));
        assertVec(10, 0, RadarMath.relative(-10, 0, 0));
        // Facing north (yaw 180): -z ahead, east to the right.
        assertVec(0, 10, RadarMath.relative(0, -10, 180));
        assertVec(10, 0, RadarMath.relative(10, 0, 180));
        // Facing east (yaw -90): +x ahead, south to the right.
        assertVec(0, 10, RadarMath.relative(10, 0, -90));
        assertVec(10, 0, RadarMath.relative(0, 10, -90));
        // Facing west (yaw 90 == -270): -x ahead.
        assertVec(0, 10, RadarMath.relative(-10, 0, 90));
        assertVec(0, 10, RadarMath.relative(-10, 0, -270));
    }

    @Test
    void northMarkerRotates() {
        assertVec(0, 1, RadarMath.north(180)); // facing north: north is up
        assertVec(0, -1, RadarMath.north(0)); // facing south: north is down
        assertVec(-1, 0, RadarMath.north(-90)); // facing east: north on the left
    }

    @Test
    void placeScalesInsideAndClampsOutside() {
        RadarMath.Placed in = RadarMath.place(new Vec2(50, 100), 200, 4, false);
        assertEquals(1.0, in.x(), EPS);
        assertEquals(2.0, in.y(), EPS);
        assertFalse(in.clamped());

        RadarMath.Placed far = RadarMath.place(new Vec2(0, 400), 200, 4, false);
        assertTrue(far.clamped());
        assertEquals(0, far.x(), EPS);
        assertEquals(4 * RadarMath.RIM_FRACTION, far.y(), EPS);

        RadarMath.Placed diag = RadarMath.place(new Vec2(-300, -300), 200, 4, false);
        assertTrue(diag.clamped());
        assertEquals(4 * RadarMath.RIM_FRACTION, Math.hypot(diag.x(), diag.y()), EPS);
        assertTrue(diag.x() < 0 && diag.y() < 0);

        // Flagged out of range: clamped even when the fuzzed position lands inside.
        RadarMath.Placed flagged = RadarMath.place(new Vec2(10, 0), 200, 4, true);
        assertTrue(flagged.clamped());
        assertEquals(4 * RadarMath.RIM_FRACTION, flagged.x(), EPS);

        // Degenerate: flagged but on top of the player -> rim, pointing up.
        RadarMath.Placed zero = RadarMath.place(new Vec2(0, 0), 200, 4, true);
        assertTrue(zero.clamped());
        assertEquals(4 * RadarMath.RIM_FRACTION, zero.y(), EPS);

        // Exactly on the radius is still inside.
        assertFalse(RadarMath.place(new Vec2(0, 200), 200, 4, false).clamped());
    }

    @Test
    void displayAngleIsClockwiseFromUp() {
        assertEquals(0, RadarMath.displayAngle(0, 1), EPS);
        assertEquals(Math.PI / 2, RadarMath.displayAngle(1, 0), EPS);
        assertEquals(Math.PI, RadarMath.displayAngle(0, -1), EPS);
        assertEquals(3 * Math.PI / 2, RadarMath.displayAngle(-1, 0), EPS);
    }

    @Test
    void compassDirections() {
        assertEquals("N", RadarMath.compass(0, -10));
        assertEquals("NE", RadarMath.compass(10, -10));
        assertEquals("E", RadarMath.compass(10, 0));
        assertEquals("SE", RadarMath.compass(10, 10));
        assertEquals("S", RadarMath.compass(0, 10));
        assertEquals("SW", RadarMath.compass(-10, 10));
        assertEquals("W", RadarMath.compass(-10, 0));
        assertEquals("NW", RadarMath.compass(-10, -10));
        assertEquals("N", RadarMath.compass(1, -10)); // slightly east of north rounds to N
        assertEquals("N", RadarMath.compass(-1, -10));
    }

    @Test
    void closestToViewPicksSmallestAngle() {
        Vec3d eye = new Vec3d(0, 64, 0);
        Vec3d look = new Vec3d(0, 0, -1); // north
        List<Vec3d> targets = List.of(new Vec3d(100, 64, 0), new Vec3d(10, 64, -100), new Vec3d(0, 64, 50));
        assertEquals(1, RadarMath.closestToView(eye, look, targets));
        assertEquals(0, RadarMath.closestToView(eye, new Vec3d(1, 0, 0), targets));
        assertEquals(-1, RadarMath.closestToView(eye, look, List.of()));
        // Height counts: looking up picks the one above.
        List<Vec3d> stacked = List.of(new Vec3d(0, 64, -20), new Vec3d(0, 90, -20));
        assertEquals(1, RadarMath.closestToView(eye, new Vec3d(0, 1, -1), stacked));
    }

    @Test
    void sweepCrossingHandlesWrap() {
        assertTrue(RadarMath.sweepCrossed(0.1, 0.5, 0.3));
        assertFalse(RadarMath.sweepCrossed(0.1, 0.5, 0.6));
        assertFalse(RadarMath.sweepCrossed(0.1, 0.5, 0.1)); // start exclusive
        assertTrue(RadarMath.sweepCrossed(0.1, 0.5, 0.5)); // end inclusive
        // Across the 2pi -> 0 seam.
        assertTrue(RadarMath.sweepCrossed(6.2, 0.1, 0.05));
        assertTrue(RadarMath.sweepCrossed(6.2, 0.1, 6.25));
        assertFalse(RadarMath.sweepCrossed(6.2, 0.1, 3.0));
        assertFalse(RadarMath.sweepCrossed(1.0, 1.0, 1.0));
    }

    @Test
    void sweepAngleTurnsOncePerPeriod() {
        assertEquals(0, RadarMath.sweepAngle(0), EPS);
        assertEquals(Math.PI, RadarMath.sweepAngle(RadarMath.SWEEP_PERIOD_TICKS / 2), EPS);
        assertEquals(0, RadarMath.sweepAngle(RadarMath.SWEEP_PERIOD_TICKS * 3), 1e-6);
    }

    @Test
    void phosphorFadesBehindSweep() {
        assertEquals(1.0, RadarMath.phosphor(1.0, 1.0), EPS);
        double shortly = RadarMath.phosphor(1.3, 1.0);
        double later = RadarMath.phosphor(4.0, 1.0);
        assertTrue(shortly > later && later >= RadarMath.PHOSPHOR_FLOOR);
        assertEquals(RadarMath.PHOSPHOR_FLOOR, RadarMath.phosphor(0.999, 1.0), 1e-3);
    }

    @Test
    void staleCheck() {
        long now = 100_000;
        assertTrue(RadarMath.stale(now, 0, 5), "never received");
        assertFalse(RadarMath.stale(now, now - 9_000, 5));
        assertTrue(RadarMath.stale(now, now - 10_001, 5));
        assertFalse(RadarMath.stale(now, now - 9_999, 0), "unknown refresh = 10 s");
        assertTrue(RadarMath.stale(now, now - 10_001, 0));
        assertFalse(RadarMath.stale(now, now - 50_000, 30));
    }

    @Test
    void heightMarker() {
        assertEquals(1, RadarMath.heightMarker(4.5));
        assertEquals(0, RadarMath.heightMarker(4.0));
        assertEquals(0, RadarMath.heightMarker(-4.0));
        assertEquals(-1, RadarMath.heightMarker(-12));
    }

    @Test
    void colourMixAndEase() {
        assertEquals(0x000000, RadarMath.mix(0x000000, 0xFFFFFF, 0));
        assertEquals(0xFFFFFF, RadarMath.mix(0x000000, 0xFFFFFF, 1));
        assertEquals(0x808080, RadarMath.mix(0x000000, 0xFFFFFF, 0.5));
        assertEquals(0.5f, RadarMath.ease(0.5f), 1e-6);
        assertEquals(0f, RadarMath.ease(-1f), 1e-6);
        assertEquals(1f, RadarMath.ease(2f), 1e-6);
        assertEquals(5, RadarMath.metres(3, 4));
    }

    @Test
    void screenLayoutParses() {
        ScreenLayout l = ScreenLayout.parse(JsonParser.parseString(
                "{\"screen\":{\"from\":[1,2,8],\"to\":[11,9,8]},\"led\":{\"from\":[3,3,3],\"to\":[2,2,2]}}").getAsJsonObject());
        assertEquals(1, l.x0(), EPS);
        assertEquals(9, l.y1(), EPS);
        assertEquals(8, l.z(), EPS);
        assertEquals(2, l.ledX0(), EPS);
        assertEquals(3, l.ledZ1(), EPS);
        ScreenLayout onlyLed = ScreenLayout.parse(JsonParser.parseString("{\"led\":{\"from\":[0,0,0],\"to\":[1,1,1]}}").getAsJsonObject());
        assertEquals(ScreenLayout.DEFAULT.x0(), onlyLed.x0(), EPS);
        assertThrows(RuntimeException.class, () -> ScreenLayout.parse(JsonParser.parseString(
                "{\"screen\":{\"from\":[1,1,1],\"to\":[1.1,5,1]}}").getAsJsonObject()));
        assertThrows(RuntimeException.class, () -> ScreenLayout.parse(JsonParser.parseString(
                "{\"screen\":{\"from\":[1,1],\"to\":[5,5,1]}}").getAsJsonObject()));
    }

    private static void assertVec(double x, double y, Vec2 v) {
        assertEquals(x, v.x(), 1e-9, "x of " + v);
        assertEquals(y, v.y(), 1e-9, "y of " + v);
    }

    @Test
    void rangeOptionsAreStepsUpToTheCap() {
        assertEquals(List.of(4, 8, 16, 32, 64, 128, 256), java.util.Arrays.stream(RadarMath.rangeOptions(256)).boxed().toList());
        assertEquals(11, RadarMath.rangeOptions(4096).length);
        assertEquals(List.of(4, 8, 16, 32, 64, 100), java.util.Arrays.stream(RadarMath.rangeOptions(100)).boxed().toList());
        assertEquals(List.of(4, 8, 10), java.util.Arrays.stream(RadarMath.rangeOptions(10)).boxed().toList());
    }

    @Test
    void stepRangeMovesOneOptionAndClamps() {
        assertEquals(128, RadarMath.stepRange(256, 4096, -1));
        assertEquals(512, RadarMath.stepRange(256, 4096, 1));
        assertEquals(4, RadarMath.stepRange(4, 4096, -1));
        assertEquals(8, RadarMath.stepRange(16, 4096, -1));
        assertEquals(256, RadarMath.stepRange(256, 256, 1));
        assertEquals(100, RadarMath.stepRange(64, 100, 1));
        assertEquals(64, RadarMath.stepRange(100, 100, -1));
        assertEquals(64, RadarMath.stepRange(90, 100, -1));
        assertEquals(100, RadarMath.stepRange(90, 100, 1)); // between options: in lands on the lower one
    }

    @Test
    void effectiveRangeFollowsTierUnlessChosen() {
        assertEquals(2048, RadarMath.effectiveRange(0, 2048));
        assertEquals(64, RadarMath.effectiveRange(64, 2048));
        assertEquals(512, RadarMath.effectiveRange(4096, 512)); // tier cap dropped below the choice
    }

    @Test
    void peripheralRangeBands() {
        assertEquals(16, RadarMath.peripheralRange(4));
        assertEquals(32, RadarMath.peripheralRange(8));
        assertEquals(32, RadarMath.peripheralRange(16));
        assertEquals(64, RadarMath.peripheralRange(32));
        assertEquals(64, RadarMath.peripheralRange(64));
        assertEquals(512, RadarMath.peripheralRange(512));
        assertEquals(100, RadarMath.peripheralRange(100)); // non-step tier cap: no band
        assertEquals(32, RadarMath.peripheralRange(20)); // between steps: band of the step at or below (16)
    }

    @Test
    void visibilityLocalVsNavigation() {
        // zoomed to 4 m on a 128 m tier
        assertEquals(RadarMath.DRAW, RadarMath.visibility("container", 3, 4, 128));
        assertEquals(RadarMath.RIM, RadarMath.visibility("container", 10, 4, 128));
        assertEquals(RadarMath.HIDDEN, RadarMath.visibility("container", 17, 4, 128));
        assertEquals(RadarMath.HIDDEN, RadarMath.visibility("my_addon", 100, 4, 128));
        assertEquals(RadarMath.RIM, RadarMath.visibility("motion", 16, 4, 128));
        assertEquals(RadarMath.RIM, RadarMath.visibility("motion_still", 16, 4, 128));
        assertEquals(RadarMath.HIDDEN, RadarMath.visibility("motion_still", 100, 4, 128));
        for (String nav : new String[] {"narrative", "structure", "manhole", "team", "last_death", "script"}) {
            assertEquals(RadarMath.DRAW, RadarMath.visibility(nav, 4, 4, 128));
            assertEquals(RadarMath.RIM, RadarMath.visibility(nav, 5000, 4, 128));
        }
        // no zoom: local blips unchanged
        assertEquals(RadarMath.DRAW, RadarMath.visibility("container", 500, 128, 128));
        assertEquals(RadarMath.DRAW, RadarMath.visibility("ore", 200, 100, 100));
    }

    @Test
    void audibleWithinRangeOnly() {
        assertTrue(RadarMath.audible(4, 4));
        assertTrue(!RadarMath.audible(4.1, 4));
    }

    @Test
    void glideFractionIsSmoothAndClamped() {
        assertEquals(0.0, RadarMath.glideFraction(0, 1000), 1e-9);
        assertEquals(0.5, RadarMath.glideFraction(500, 1000), 1e-9);
        assertEquals(1.0, RadarMath.glideFraction(1000, 1000), 1e-9);
        assertEquals(1.0, RadarMath.glideFraction(5000, 1000), 1e-9);
        assertEquals(0.0, RadarMath.glideFraction(-50, 1000), 1e-9);
        assertEquals(1.0, RadarMath.glideFraction(10, 0), 1e-9);
    }

    @Test
    void visibilityFollowsTheShownPositionWhileGliding() {
        // zoom 16 m (band to 32 m, tier range 256): a biosign blip glides from 10 m to 40 m after a snapshot
        int zoom = 16;
        int tier = 256;
        double start = RadarMath.shownDistance(0, 0, 10, 0, 40, 0, RadarMath.glideFraction(0, 1000));
        assertEquals(10, start, 1e-9);
        // right after the snapshot it is still drawn at 10 m: must stay DRAW (the old code used the 40 m target: HIDDEN at once)
        assertEquals(RadarMath.DRAW, RadarMath.visibility("biosign", start, zoom, tier));
        assertEquals(RadarMath.HIDDEN, RadarMath.visibility("biosign", 40, zoom, tier));
        // halfway it is at 25 m: rim band
        double mid = RadarMath.shownDistance(0, 0, 10, 0, 40, 0, RadarMath.glideFraction(500, 1000));
        assertEquals(25, mid, 1e-9);
        assertEquals(RadarMath.RIM, RadarMath.visibility("biosign", mid, zoom, tier));
        // glide done: hidden, never back and forth
        double end = RadarMath.shownDistance(0, 0, 10, 0, 40, 0, RadarMath.glideFraction(1000, 1000));
        assertEquals(RadarMath.HIDDEN, RadarMath.visibility("biosign", end, zoom, tier));
        // the player position is subtracted
        assertEquals(5, RadarMath.shownDistance(3, 4, 0, 0, 0, 0, 1), 1e-9);
    }
}
