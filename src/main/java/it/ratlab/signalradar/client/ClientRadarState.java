// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.net.SnapshotPayload;
import it.ratlab.signalradar.scan.ScanSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side holder of the latest scan snapshot. The phase 3 renderer reads {@link #latest()} and
 * {@link #receivedAtMillis()} (a snapshot older than about twice {@code scanRefreshSeconds} means the radar is no
 * longer being scanned). Client only: never referenced from common code.
 */
public final class ClientRadarState {
    @Nullable
    private static volatile ScanSnapshot latest;
    private static volatile long receivedAtMillis;

    private ClientRadarState() {}

    static void accept(SnapshotPayload payload) {
        latest = payload.snapshot();
        receivedAtMillis = System.currentTimeMillis();
    }

    @Nullable
    public static ScanSnapshot latest() {
        return latest;
    }

    public static long receivedAtMillis() {
        return receivedAtMillis;
    }

    /** Forget the last snapshot (logout / world change). */
    public static void clear() {
        latest = null;
        receivedAtMillis = 0L;
    }
}
