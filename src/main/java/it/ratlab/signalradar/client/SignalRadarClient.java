// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.net.RadarNetworking;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only setup. Touched only when running on the physical client. */
public final class SignalRadarClient {
    private SignalRadarClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, RadarClientConfig.SPEC);
        modBus.addListener(ModelEvent.RegisterAdditional.class, RadarItemRenderer::registerModels);
        modBus.addListener(ModelEvent.RegisterAdditional.class, CustomAddonModels::registerModels);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, CustomAddonModels::modifyBakingResult);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item.class, CustomAddonModels::registerColors);
        modBus.addListener((RegisterClientExtensionsEvent e) -> e.registerItem(new RadarClientExtensions(), ModItems.RADAR.get()));
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ModMenus.ADDONS.get(), AddonScreen::new));
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new RadarScreenLoader()));
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> RadarIcons.clear()));
        RadarNetworking.clientSnapshot = p -> {
            ClientRadarState.accept(p);
            RadarClientSounds.onSnapshot(p.snapshot());
        };
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            RadarClock.tick();
            RaiseState.tick();
            RadarClientSounds.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientRadarState.clear();
            RadarIcons.clear();
            RaiseState.reset();
            RadarClock.reset();
            RadarClientSounds.reset();
        });
    }
}
