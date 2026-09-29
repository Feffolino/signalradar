// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

/** 2D vector. On the display: {@code x} = right, {@code y} = up. */
public record Vec2(double x, double y) {
    public double length() {
        return Math.sqrt(x * x + y * y);
    }
}
