// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Game test players. Normally the vanilla mock player (real login into the player list). With KubeJS loaded that login
 * fails: KubeJS sends its sync payloads to every joining player and the mock connection refuses unnegotiated payloads.
 * A FakePlayer is no substitute because KubeJS gives fake players no stages. So with KubeJS a plain
 * {@link ServerPlayer} (creative, like the mock player) with a connection that drops every packet is used; it is not in
 * the player list nor added to the level.
 */
public final class TestPlayers {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestPlayers() {}

    public static boolean quiet() {
        return ModList.get().isLoaded("kubejs");
    }

    @SuppressWarnings("removal")
    public static ServerPlayer create(GameTestHelper h) {
        if (!quiet()) {
            return h.makeMockServerPlayerInLevel();
        }
        ServerLevel level = h.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "sr_test_" + COUNTER.incrementAndGet());
        ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        p.connection = new QuietListener(level.getServer(), p, profile);
        return p;
    }

    /** Drops every outgoing packet. */
    private static final class QuietListener extends ServerGamePacketListenerImpl {
        QuietListener(MinecraftServer server, ServerPlayer player, GameProfile profile) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player, CommonListenerCookie.createInitial(profile, false));
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}

        @Override
        public void tick() {}

        @Override
        public void disconnect(Component reason) {}
    }
}
