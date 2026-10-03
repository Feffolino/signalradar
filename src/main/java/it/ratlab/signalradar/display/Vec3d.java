// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

/** Minecraft-free 3D vector (world coordinates) so the display math can be unit tested. */
public record Vec3d(double x, double y, double z) {
    public Vec3d minus(Vec3d o) {
        return new Vec3d(x - o.x, y - o.y, z - o.z);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
