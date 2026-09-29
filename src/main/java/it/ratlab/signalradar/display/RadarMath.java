// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import java.util.List;

/**
 * Pure display math (no Minecraft classes, unit tested).
 * <p>
 * Conventions: Minecraft yaw in degrees (0 = south/+z, 90 = west, 180 = north, -90 = east). The display is
 * heading-up: the player's facing points up. Display angles are radians clockwise from display-up in [0, 2pi).
 */
public final class RadarMath {
    public static final double TAU = Math.PI * 2;
    /** Ticks for one full sweep turn. */
    public static final double SWEEP_PERIOD_TICKS = 50.0;
    /** Blip brightness never drops below this between sweeps. */
    public static final double PHOSPHOR_FLOOR = 0.12;
    /** Clamped blips sit at this fraction of the display radius. */
    public static final double RIM_FRACTION = 0.92;
    /** Height difference (blocks) above which a blip shows a height arrow. */
    public static final double HEIGHT_THRESHOLD = 4.0;
    /** Stale limit used when the snapshot's refresh period is unknown (0 or less). */
    public static final long DEFAULT_STALE_MILLIS = 10_000L;

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private RadarMath() {}

    /** Angle in [0, 2pi). */
    public static double wrap(double a) {
        double r = a % TAU;
        return r < 0 ? r + TAU : r;
    }

    /**
     * World offset (target minus player, horizontal) to player-relative blocks: {@code x} = to the player's right,
     * {@code y} = ahead.
     */
    public static Vec2 relative(double dx, double dz, double yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double forward = -dx * sin + dz * cos;
        double right = -dx * cos - dz * sin;
        return new Vec2(right, forward);
    }

    /** A blip placed on the display. {@code clamped} = pinned to the rim (draw an outward arrow). */
    public record Placed(double x, double y, boolean clamped) {}

    /**
     * Scales a player-relative offset (blocks) to display units ({@code range} blocks = {@code radius} units). Targets
     * beyond the radius, or flagged {@code outOfRange}, are clamped to {@link #RIM_FRACTION} of the radius.
     */
    public static Placed place(Vec2 rel, double range, double radius, boolean outOfRange) {
        double scale = range > 0 ? radius / range : 0;
        double x = rel.x() * scale;
        double y = rel.y() * scale;
        double len = Math.sqrt(x * x + y * y);
        if (!outOfRange && len <= radius) {
            return new Placed(x, y, false);
        }
        double rim = radius * RIM_FRACTION;
        if (len < 1e-9) {
            return new Placed(0, rim, true);
        }
        return new Placed(x / len * rim, y / len * rim, true);
    }

    /** Display angle of a display point (clockwise from up). */
    public static double displayAngle(double x, double y) {
        return wrap(Math.atan2(x, y));
    }

    /** Direction of north on the display (unit vector) for a yaw. */
    public static Vec2 north(double yawDeg) {
        return relative(0, -1, yawDeg);
    }

    /** 8-point compass name of a horizontal world offset (N = -z, E = +x). */
    public static String compass(double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(bearing / 45.0), 8L);
        return COMPASS[i];
    }

    /**
     * Index of the target whose direction from {@code eye} makes the smallest angle with {@code look}, or -1 when the
     * list is empty. A target at the eye position counts as dead ahead.
     */
    public static int closestToView(Vec3d eye, Vec3d look, List<Vec3d> targets) {
        double ll = look.length();
        int best = -1;
        double bestCos = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < targets.size(); i++) {
            Vec3d d = targets.get(i).minus(eye);
            double dl = d.length();
            double cos = dl < 1e-9 || ll < 1e-9 ? 1.0 : (d.x() * look.x() + d.y() * look.y() + d.z() * look.z()) / (dl * ll);
            if (cos > bestCos) {
                bestCos = cos;
                best = i;
            }
        }
        return best;
    }

    /** Sweep angle for a (fractional) game time in ticks. */
    public static double sweepAngle(double ticks) {
        return wrap(ticks / SWEEP_PERIOD_TICKS * TAU);
    }

    /**
     * True when the sweep, moving clockwise from {@code prev} to {@code cur}, passed {@code blip} (exclusive start,
     * inclusive end). No movement never counts.
     */
    public static boolean sweepCrossed(double prev, double cur, double blip) {
        double moved = wrap(cur - prev);
        if (moved <= 0) {
            return false;
        }
        double toBlip = wrap(blip - prev);
        return toBlip > 0 && toBlip <= moved;
    }

    /** Phosphor brightness 0..1 of a blip: 1 right after the sweep passed it, fading until the next pass. */
    public static double phosphor(double sweep, double blip) {
        double behind = wrap(sweep - blip) / TAU;
        double b = 1.0 - behind;
        return Math.max(PHOSPHOR_FLOOR, b * b);
    }

    /** The snapshot is older than twice its refresh period (10 s when unknown) or was never received. */
    public static boolean stale(long nowMillis, long receivedAtMillis, int refreshSeconds) {
        if (receivedAtMillis <= 0) {
            return true;
        }
        long limit = refreshSeconds > 0 ? refreshSeconds * 2000L : DEFAULT_STALE_MILLIS;
        return nowMillis - receivedAtMillis > limit;
    }

    /** +1 target above, -1 below, 0 roughly level ({@link #HEIGHT_THRESHOLD}). */
    public static int heightMarker(double dy) {
        if (dy > HEIGHT_THRESHOLD) {
            return 1;
        }
        return dy < -HEIGHT_THRESHOLD ? -1 : 0;
    }

    /** Smoothstep 0..1. */
    public static float ease(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return c * c * (3f - 2f * c);
    }

    /** Linear mix of two 0xRRGGBB colours. */
    public static int mix(int from, int to, double t) {
        double c = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * c);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * c);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * c);
        return (r << 16) | (g << 8) | b;
    }

    /** Horizontal distance rounded to whole metres (blocks). */
    public static int metres(double dx, double dz) {
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
