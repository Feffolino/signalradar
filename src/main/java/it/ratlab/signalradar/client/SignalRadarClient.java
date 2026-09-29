// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup. Touched only when running on the physical client. */
public final class SignalRadarClient {
    private SignalRadarClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(SignalRadarClient::setup);
        it.ratlab.signalradar.net.RadarNetworking.clientSnapshot = ClientRadarState::accept;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> ClientRadarState.clear());
    }

    private static void setup(FMLClientSetupEvent event) {
        // Model overrides radar_t1..t4 use predicate signalradar:tier = tier / 4.
        event.enqueueWork(() -> ItemProperties.register(ModItems.RADAR.get(), SignalRadar.id("tier"),
                (stack, level, entity, seed) -> RadarItem.tier(stack) / (float) RadarItem.MAX_TIER));
    }
}
