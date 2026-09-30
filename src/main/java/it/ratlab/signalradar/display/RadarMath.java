// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import java.util.List;
import java.util.Set;

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

    /** Display range steps of the zoom, metres. */
    public static final int[] RANGE_STEPS = {4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};

    /** Selectable ranges for a tier range {@code cap}: the steps up to the cap, plus the cap itself when it is not a step. */
    public static int[] rangeOptions(int cap) {
        if (cap <= 0) {
            return new int[] {Math.max(cap, 0)};
        }
        int n = 0;
        for (int s : RANGE_STEPS) {
            if (s < cap) {
                n++;
            }
        }
        int[] out = new int[n + 1];
        int i = 0;
        for (int s : RANGE_STEPS) {
            if (s < cap) {
                out[i++] = s;
            }
        }
        out[n] = cap;
        return out;
    }

    /** Range actually displayed: the chosen one, or the tier range when nothing is chosen (<= 0) or the cap dropped below it. */
    public static int effectiveRange(int chosen, int cap) {
        return chosen <= 0 || chosen > cap ? cap : chosen;
    }

    /**
     * Next display range: {@code dir} +1 = larger (zoom out), -1 = smaller (zoom in), one option per call, clamped to
     * {@link #rangeOptions}. A current range between two options steps to the one below it (zoom in) or the one above (zoom out).
     */
    public static int stepRange(int current, int cap, int dir) {
        int[] o = rangeOptions(cap);
        int idx = 0;
        for (int i = 0; i < o.length; i++) {
            if (o[i] <= current) {
                idx = i;
            }
        }
        int step = Integer.signum(dir);
        if (step < 0 && o[idx] < current) {
            step = 0; // between two options: zooming in lands on the one below
        }
        int next = Math.max(0, Math.min(o.length - 1, idx + step));
        return o[next];
    }

    /** Navigation categories always stay on the display (inside the range, or on the rim with an arrow beyond it). */
    public static final Set<String> NAVIGATION_CATEGORIES = Set.of("narrative", "structure", "manhole", "team", "last_death", "script");

    /** {@link #visibility}: draw at its real spot. */
    public static final int DRAW = 0;
    /** {@link #visibility}: pin to the rim with an arrow. */
    public static final int RIM = 1;
    /** {@link #visibility}: not drawn at all. */
    public static final int HIDDEN = 2;

    /** Local categories (container, loot, ore, biosign, motion, custom addons) are everything that is not navigation. */
    public static boolean isNavigation(String category) {
        return NAVIGATION_CATEGORIES.contains(category);
    }

    /**
     * Outer edge of the rim band of local blips for a zoomed display range: 4 gives 16, 8 gives 32, 16 gives 32,
     * 32 gives 64, 64 and up (and anything below 4) give the range itself (no band). A value between steps uses the
     * band of the step at or below it, so a non-step tier cap such as 100 has no band.
     */
    public static int peripheralRange(int zoomRange) {
        int step = 0;
        for (int s : RANGE_STEPS) {
            if (s <= zoomRange) {
                step = s;
            }
        }
        return switch (step) {
            case 4 -> 16;
            case 8, 16 -> 32;
            case 32 -> 64;
            default -> zoomRange;
        };
    }

    /**
     * How a blip is shown: {@link #DRAW}, {@link #RIM} or {@link #HIDDEN}. Navigation blips are always shown (rim beyond
     * the range). Local blips, with a zoom active ({@code zoomRange < tierRange}), are drawn up to the zoom range, on the
     * rim up to {@link #peripheralRange}, and hidden beyond. Without a zoom nothing changes ({@code DRAW}; the caller
     * still clamps what is beyond the tier range).
     */
    public static int visibility(String category, double dist, int zoomRange, int tierRange) {
        if (isNavigation(category)) {
            return dist <= zoomRange ? DRAW : RIM;
        }
        if (zoomRange >= tierRange || dist <= zoomRange) {
            return DRAW;
        }
        return dist <= peripheralRange(zoomRange) ? RIM : HIDDEN;
    }

    /** Smoothstepped glide progress 0..1 of a blip {@code elapsedMillis} after its snapshot arrived. */
    public static double glideFraction(long elapsedMillis, long glideMillis) {
        if (glideMillis <= 0) {
            return 1;
        }
        double t = Math.max(0, Math.min(1, elapsedMillis / (double) glideMillis));
        return t * t * (3 - 2 * t);
    }

    /**
     * Horizontal distance from the player ({@code px, pz}) to where a blip is <b>drawn</b> while it glides from
     * {@code (fromX, fromZ)} to its snapshot position {@code (toX, toZ)}. Visibility (zoom band, rim, hidden), grouping and the
     * icon limit must use this, not the snapshot position: otherwise a blip whose new position lies across a zoom boundary is
     * hidden (or pinned to the rim) at once while it is still drawn gliding, and pops back ~1 s later when the glide ends.
     */
    public static double shownDistance(double px, double pz, double fromX, double fromZ, double toX, double toZ, double glide) {
        double x = fromX + (toX - fromX) * glide - px;
        double z = fromZ + (toZ - fromZ) * glide - pz;
        return Math.sqrt(x * x + z * z);
    }

    /** True when a blip is close enough to sound (inside the effective display range). */
    public static boolean audible(double dist, int zoomRange) {
        return dist <= zoomRange;
    }

    /** Horizontal distance rounded to whole metres (blocks). */
    public static int metres(double dx, double dz) {
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
