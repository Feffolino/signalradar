// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.net.RadarNetworking;
import it.ratlab.signalradar.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only setup. Touched only when running on the physical client. */
public final class SignalRadarClient {
    private SignalRadarClient() {}

    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, RadarClientConfig.SPEC);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(RadarZoom.ZOOM_IN);
            e.register(RadarZoom.ZOOM_OUT);
        });
        modBus.addListener(RadarItemRenderer::registerModels);
        modBus.addListener(CustomAddonModels::registerModels);
        modBus.addListener(CustomAddonPack::register);
        modBus.addListener(CustomAddonModels::modifyBakingResult);
        modBus.addListener(CustomAddonModels::registerColors);
        modBus.addListener((FMLClientSetupEvent e) -> {
            e.enqueueWork(() -> MenuScreens.register(ModMenus.ADDONS.get(), AddonScreen::new));
        });
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new RadarScreenLoader()));
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) rm -> RadarIcons.clear()));
        RadarNetworking.clientSnapshot = p -> {
            ClientRadarState.accept(p);
            RadarClientSounds.onSnapshot(p.snapshot());
        };
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                RadarClock.tick();
                RaiseState.tick();
                RadarClientSounds.tick();
                RadarZoom.tick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent e) -> {
            // Raised radar: the wheel is the zoom, never the hotbar.
            if (RaiseState.raised() && net.minecraft.client.Minecraft.getInstance().screen == null) {
                e.setCanceled(true);
                double d = e.getScrollDelta();
                if (d != 0) {
                    // Wheel up = larger range (zoom out), wheel down = smaller range (zoom in); play-test request.
                    RadarZoom.step(d > 0 ? 1 : -1);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            RadarZoom.flush();
            ClientRadarState.clear();
            RadarIcons.clear();
            RaiseState.reset();
            RadarClock.reset();
            RadarClientSounds.reset();
            RadarZoom.reset();
        });
    }
}
