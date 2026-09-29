// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.net;

import java.util.function.Consumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RadarNetworking {
    /** Set by the client entry point; stays a no-op on a dedicated server (which never receives it). */
    public static Consumer<SnapshotPayload> clientSnapshot = p -> {};

    private RadarNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SnapshotPayload.TYPE, SnapshotPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientSnapshot.accept(p)));
    }
}
