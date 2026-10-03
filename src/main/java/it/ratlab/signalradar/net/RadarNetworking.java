// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.net;

import it.ratlab.signalradar.SignalRadar;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RadarNetworking {
    /** Set by the client entry point; stays a no-op on a dedicated server (which never receives it). */
    public static Consumer<SnapshotPayload> clientSnapshot = p -> {};

    /** 2: snapshot carries range + refreshSeconds (phase 3); 3: snapshot carries the charged flag (phase 4); 4: snapshot carries the motion addon radius (phase 5); 5: icon spec. */
    public static final String PROTOCOL = "5";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SignalRadar.id("main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private RadarNetworking() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SnapshotPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SnapshotPayload::write)
                .decoder(SnapshotPayload::read)
                .consumerMainThread(RadarNetworking::handleSnapshot)
                .add();
    }

    private static void handleSnapshot(SnapshotPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> clientSnapshot.accept(msg));
        ctx.setPacketHandled(true);
    }

    public static void sendTo(ServerPlayer player, SnapshotPayload msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToPlayer(ServerPlayer player, it.ratlab.signalradar.scan.ScanSnapshot snapshot) {
        sendTo(player, new SnapshotPayload(snapshot));
    }
}
