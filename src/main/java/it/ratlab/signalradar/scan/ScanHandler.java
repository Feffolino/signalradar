// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.net.SnapshotPayload;
import it.ratlab.signalradar.target.TargetManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side glue: scans for players holding a radar, runs the structure queue. */
public final class ScanHandler {
    /** Game time of each player's next scan. */
    private static final Map<UUID, Long> NEXT_SCAN = new HashMap<>();

    private ScanHandler() {}

    public static void register(IEventBus bus) {
        bus.addListener(ScanHandler::onPlayerTick);
        bus.addListener(ScanHandler::onServerTick);
        bus.addListener(ScanHandler::onLogout);
    }

    /** Main hand first, then offhand. Empty when the player holds no radar. */
    public static ItemStack heldRadar(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof RadarItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof RadarItem ? off : ItemStack.EMPTY;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack radar = heldRadar(player);
        if (radar.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        Long next = NEXT_SCAN.get(player.getUUID());
        if (next != null && now < next) {
            return;
        }
        ScanSettings settings = ScanSettings.fromConfig();
        NEXT_SCAN.put(player.getUUID(), now + settings.refreshTicks());
        sendScan(player, radar, settings, now);
    }

    /** Runs one scan now (charges the radar) and sends the snapshot. */
    public static ScanSnapshot sendScan(ServerPlayer player, ItemStack radar, ScanSettings settings, long now) {
        ServerLevel level = player.serverLevel();
        int range = settings.range(RadarItem.tier(radar));
        BlockLocatorScan.Budget budget = new BlockLocatorScan.Budget(it.ratlab.signalradar.SignalRadarConfig.maxBlockChecksPerScan());
        ScanSnapshot snapshot = RadarScanner.scan(radar, player.getUUID(), player.position(), now, settings, TargetManager.all(),
                def -> Locators.locate(player, def, range, level, now, budget));
        PacketDistributor.sendToPlayer(player, new SnapshotPayload(snapshot));
        return snapshot;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        StructureLookupService svc = StructureLookupService.INSTANCE;
        if (svc.pending() == 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        svc.tick(server::getLevel, StructureCacheData.get(server), server.overworld().getGameTime(),
                it.ratlab.signalradar.SignalRadarConfig.structureLookupsPerTick());
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        NEXT_SCAN.remove(id);
        BlockLocatorScan.INSTANCE.forget(id);
    }

    /** Server stopped / test hook. */
    public static void reset() {
        NEXT_SCAN.clear();
        StructureLookupService.INSTANCE.clearQueue();
        BlockLocatorScan.INSTANCE.clear();
    }
}
