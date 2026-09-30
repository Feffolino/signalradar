// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.net;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the latest scan of the radar the player is holding. */
public record SnapshotPayload(ScanSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<SnapshotPayload> TYPE = new Type<>(SignalRadar.id("snapshot"));
    /** Sanity cap so a broken server cannot make the client allocate absurd lists. */
    private static final int MAX_BLIPS = 4096;

    /** Longest icon spec on the wire. */
    private static final int MAX_ICON = 256;

    private static final int FLAG_OUT_OF_RANGE = 1;
    private static final int FLAG_FOUND = 2;

    public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = StreamCodec.of(SnapshotPayload::write, SnapshotPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SnapshotPayload p) {
        ScanSnapshot s = p.snapshot;
        buf.writeVarInt(s.tier());
        buf.writeVarInt(s.energy());
        buf.writeVarInt(s.capacity());
        buf.writeVarInt(s.range());
        buf.writeVarInt(s.refreshSeconds());
        buf.writeBoolean(s.noSignal());
        buf.writeBoolean(s.charged());
        buf.writeLong(s.gameTime());
        buf.writeVarInt(s.motionRadius());
        buf.writeVarInt(s.blips().size());
        for (Blip b : s.blips()) {
            buf.writeUtf(b.id());
            buf.writeUtf(b.category());
            buf.writeInt(b.color());
            buf.writeDouble(b.x());
            buf.writeDouble(b.y());
            buf.writeDouble(b.z());
            ComponentSerialization.STREAM_CODEC.encode(buf, b.name());
            buf.writeByte((b.outOfRange() ? FLAG_OUT_OF_RANGE : 0) | (b.found() ? FLAG_FOUND : 0));
            buf.writeUtf(b.icon(), MAX_ICON);
        }
    }

    private static SnapshotPayload read(RegistryFriendlyByteBuf buf) {
        int tier = buf.readVarInt();
        int energy = buf.readVarInt();
        int capacity = buf.readVarInt();
        int range = buf.readVarInt();
        int refresh = buf.readVarInt();
        boolean noSignal = buf.readBoolean();
        boolean charged = buf.readBoolean();
        long time = buf.readLong();
        int motionRadius = buf.readVarInt();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_BLIPS) {
            throw new io.netty.handler.codec.DecoderException("Radar snapshot with " + n + " blips (max " + MAX_BLIPS + ")");
        }
        List<Blip> blips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            String category = buf.readUtf();
            int color = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            var name = ComponentSerialization.STREAM_CODEC.decode(buf);
            int flags = buf.readByte();
            String icon = buf.readUtf(MAX_ICON);
            blips.add(new Blip(id, category, color, x, y, z, name, (flags & FLAG_OUT_OF_RANGE) != 0, (flags & FLAG_FOUND) != 0, icon));
        }
        return new SnapshotPayload(new ScanSnapshot(tier, energy, capacity, range, refresh, noSignal, time, blips, charged, motionRadius));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
