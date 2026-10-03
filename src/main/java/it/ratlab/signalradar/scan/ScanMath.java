// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Distance, fuzz and name reveal rules (spec section 2). Pure functions, no world access. */
public final class ScanMath {
    /** Fuzz stays constant for this many ticks (10 s) so blips wobble slowly. */
    public static final int FUZZ_BUCKET_TICKS = 200;

    private ScanMath() {}

    /** Horizontal (x/z) distance: the display is a top-down radar. */
    public static double distance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static long bucket(long gameTime) {
        return Math.floorDiv(gameTime, (long) FUZZ_BUCKET_TICKS);
    }

    /** Maximum offset in blocks: {@code maxFuzz * clamp(dist / range, 0, 1)}. */
    public static double fuzzMagnitude(int maxFuzz, double dist, double range) {
        double f = range <= 0 ? 1.0 : Mth.clamp(dist / range, 0.0, 1.0);
        return maxFuzz * f;
    }

    /** Deterministic per (player, target, 10 s bucket); offsets x and z only, |offset| <= magnitude. */
    public static Vec3 fuzz(UUID player, String targetId, long gameTime, Vec3 pos, double magnitude) {
        if (magnitude <= 0) {
            return pos;
        }
        long h = mix(player.getMostSignificantBits());
        h = mix(h ^ player.getLeastSignificantBits());
        h = mix(h ^ targetId.hashCode());
        h = mix(h ^ bucket(gameTime));
        double angle = unit(h) * (Math.PI * 2);
        double radius = magnitude * Math.sqrt(unit(mix(h + 1)));
        return new Vec3(pos.x + Math.cos(angle) * radius, pos.y, pos.z + Math.sin(angle) * radius);
    }

    /** Name is shown once the (horizontal) distance is within the reveal distance. */
    public static boolean revealed(double dist, int revealDistance) {
        return dist <= revealDistance;
    }

    /** splitmix64 finalizer. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1) from the top 53 bits. */
    private static double unit(long z) {
        return (z >>> 11) * 0x1.0p-53;
    }
}
