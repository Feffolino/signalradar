// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.net;

import java.util.function.Consumer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RadarNetworking {
    /** Set by the client entry point; stays a no-op on a dedicated server (which never receives it). */
    public static Consumer<SnapshotPayload> clientSnapshot = p -> {};

    /** 2: snapshot carries range + refreshSeconds (phase 3); 3: snapshot carries the charged flag (phase 4); 4: snapshot carries the motion addon radius (phase 5). */
    public static final String PROTOCOL = "4";

    private RadarNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PROTOCOL);
        r.playToClient(SnapshotPayload.TYPE, SnapshotPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientSnapshot.accept(p)));
    }
}
