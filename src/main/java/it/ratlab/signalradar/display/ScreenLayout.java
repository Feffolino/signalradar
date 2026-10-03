// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Where the display and the status LED sit on the model, in model units (0..16), from
 * {@code assets/signalradar/radar_screen.json}. The display is drawn on the plane {@code z = screen z}, facing +z
 * (the element's south face). The LED is a box whose south (+z) and top (+y) faces are painted.
 */
public record ScreenLayout(double x0, double y0, double x1, double y1, double z,
                           double ledX0, double ledY0, double ledZ0, double ledX1, double ledY1, double ledZ1) {
    /** Values of the shipped model (elements {@code screen} and {@code led}). */
    public static final ScreenLayout DEFAULT = new ScreenLayout(2.25, 5, 13.75, 13.75, 10.25,
            12.25, 13.85, 10.75, 13.25, 14.4, 11);

    public double width() {
        return x1 - x0;
    }

    public double height() {
        return y1 - y0;
    }

    /**
     * Parses {@code {"screen":{"from":[x,y,z],"to":[x,y,z]},"led":{"from":[..],"to":[..]}}}. A missing part keeps the
     * default of that part.
     *
     * @throws RuntimeException on malformed numbers or a screen rectangle smaller than 0.5 x 0.5
     */
    public static ScreenLayout parse(JsonObject o) {
        ScreenLayout d = DEFAULT;
        double[] sf = {d.x0, d.y0, d.z};
        double[] st = {d.x1, d.y1, d.z};
        double[] lf = {d.ledX0, d.ledY0, d.ledZ0};
        double[] lt = {d.ledX1, d.ledY1, d.ledZ1};
        if (o.has("screen")) {
            JsonObject s = o.getAsJsonObject("screen");
            sf = vec(s, "from");
            st = vec(s, "to");
        }
        if (o.has("led")) {
            JsonObject l = o.getAsJsonObject("led");
            lf = vec(l, "from");
            lt = vec(l, "to");
        }
        double x0 = Math.min(sf[0], st[0]);
        double x1 = Math.max(sf[0], st[0]);
        double y0 = Math.min(sf[1], st[1]);
        double y1 = Math.max(sf[1], st[1]);
        if (x1 - x0 < 0.5 || y1 - y0 < 0.5) {
            throw new IllegalArgumentException("screen rectangle too small: " + (x1 - x0) + " x " + (y1 - y0));
        }
        return new ScreenLayout(x0, y0, x1, y1, Math.max(sf[2], st[2]),
                Math.min(lf[0], lt[0]), Math.min(lf[1], lt[1]), Math.min(lf[2], lt[2]),
                Math.max(lf[0], lt[0]), Math.max(lf[1], lt[1]), Math.max(lf[2], lt[2]));
    }

    private static double[] vec(JsonObject o, String key) {
        JsonArray a = o.getAsJsonArray(key);
        if (a == null || a.size() != 3) {
            throw new IllegalArgumentException("'" + key + "' needs 3 numbers");
        }
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }
}
