// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.AddonCache;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.addon.detect.MotionTracker;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.net.SnapshotPayload;
import it.ratlab.signalradar.target.TargetManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
    /** Per-player scan schedule. */
    private static final class State {
        /** Game time of the next snapshot (base period, or the shortest addon refresh while paid up). */
        long nextSend;
        /** Game time of the next base charge. */
        long nextCharge;
        /** The current charge period was paid: snapshots in between are free. */
        boolean paid;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

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
        State st = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        if (now < st.nextSend) {
            return;
        }
        ScanSettings base = ScanSettings.fromConfig();
        List<AddonSettings> addons = AddonRules.active(radar);
        int sendSeconds = base.refreshSeconds();
        for (AddonSettings a : addons) {
            sendSeconds = Math.min(sendSeconds, a.refreshSeconds());
        }
        boolean pay = now >= st.nextCharge || st.nextCharge - now > base.refreshTicks(); // second: game time went backwards
        ScanSnapshot snap;
        if (pay) {
            snap = sendScan(player, radar, base.withRefreshSeconds(sendSeconds), now, addons, RadarScanner.Charge.PAY);
            st.nextCharge = now + base.refreshTicks();
            st.paid = !snap.noSignal();
        } else if (st.paid) {
            snap = sendScan(player, radar, base.withRefreshSeconds(sendSeconds), now, addons, RadarScanner.Charge.FREE);
        } else {
            snap = RadarScanner.noSignal(radar, base, now, false);
            PacketDistributor.sendToPlayer(player, new SnapshotPayload(snap));
        }
        st.nextSend = now + (snap.noSignal() ? base.refreshTicks() : Math.max(1, sendSeconds) * 20L);
    }

    /** Runs one charged scan now (base cost + installed addons) and sends the snapshot. */
    public static ScanSnapshot sendScan(ServerPlayer player, ItemStack radar, ScanSettings settings, long now) {
        return sendScan(player, radar, settings, now, AddonRules.active(radar), RadarScanner.Charge.PAY);
    }

    private static ScanSnapshot sendScan(ServerPlayer player, ItemStack radar, ScanSettings settings, long now,
                                         List<AddonSettings> addons, RadarScanner.Charge charge) {
        ServerLevel level = player.serverLevel();
        int tier = RadarItem.tier(radar);
        int range = settings.range(tier);
        BlockLocatorScan.Budget budget = new BlockLocatorScan.Budget(it.ratlab.signalradar.SignalRadarConfig.maxBlockChecksPerScan());
        String dimension = level.dimension().location().toString();
        Function<AddonSettings, List<Hit>> detect = a -> {
            int radius = a.radius(tier, range);
            return AddonCache.INSTANCE.get(player.getUUID(), a.def().id(), now, a.refreshSeconds() * 20L, radius, dimension,
                    () -> Detectors.run(a, player, level, radius, budget, now));
        };
        ScanSnapshot snapshot = RadarScanner.scan(radar, player.getUUID(), player.position(), now, settings, TargetManager.all(),
                def -> Locators.locate(player, def, range, level, now, budget), addons, detect, charge);
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
        STATES.remove(id);
        BlockLocatorScan.INSTANCE.forget(id);
        AddonCache.INSTANCE.forget(id);
        MotionTracker.INSTANCE.forget(id);
    }

    /** Server stopped / test hook. */
    public static void reset() {
        STATES.clear();
        StructureLookupService.INSTANCE.clearQueue();
        BlockLocatorScan.INSTANCE.clear();
        AddonCache.INSTANCE.clear();
        MotionTracker.INSTANCE.clear();
    }
}
