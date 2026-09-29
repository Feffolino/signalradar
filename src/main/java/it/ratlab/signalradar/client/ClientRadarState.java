// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.display.Vec3d;
import it.ratlab.signalradar.net.SnapshotPayload;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanSnapshot;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder of the latest scan snapshot. The renderer reads {@link #latest()} and {@link #receivedAtMillis()}
 * (older than twice the snapshot's refresh period = no data) and {@link #position} (blips glide from where they were
 * shown to their new position over {@link #GLIDE_MILLIS}). Client only, main thread only.
 */
public final class ClientRadarState {
    public static final long GLIDE_MILLIS = 1000L;

    @Nullable
    private static ScanSnapshot latest;
    private static long receivedAtMillis;
    /** Where each blip id started gliding from when the latest snapshot arrived. */
    private static final Map<String, Vec3d> FROM = new HashMap<>();

    private ClientRadarState() {}

    static void accept(SnapshotPayload payload) {
        long now = System.currentTimeMillis();
        Map<String, Vec3d> shown = new HashMap<>();
        if (latest != null) {
            for (Blip b : latest.blips()) {
                shown.put(b.id(), position(b, now));
            }
        }
        FROM.clear();
        FROM.putAll(shown);
        latest = payload.snapshot();
        receivedAtMillis = now;
    }

    @Nullable
    public static ScanSnapshot latest() {
        return latest;
    }

    public static long receivedAtMillis() {
        return receivedAtMillis;
    }

    /** Glided world position of a blip of the latest snapshot. */
    public static Vec3d position(Blip b, long nowMillis) {
        Vec3d to = new Vec3d(b.x(), b.y(), b.z());
        Vec3d from = FROM.get(b.id());
        if (from == null) {
            return to;
        }
        double t = Math.max(0, Math.min(1, (nowMillis - receivedAtMillis) / (double) GLIDE_MILLIS));
        t = t * t * (3 - 2 * t);
        return new Vec3d(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t, from.z() + (to.z() - from.z()) * t);
    }

    /** Forget the last snapshot (logout / world change). */
    public static void clear() {
        latest = null;
        receivedAtMillis = 0L;
        FROM.clear();
    }
}
