// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import com.mojang.logging.LogUtils;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.item.RadarEnergyStorage;
import it.ratlab.signalradar.registry.ModComponents;
import it.ratlab.signalradar.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(SignalRadar.MOD_ID)
public final class SignalRadar {
    public static final String MOD_ID = "signalradar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SignalRadar(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.STARTUP, SignalRadarStartupConfig.SPEC); // loaded now
        container.registerConfig(ModConfig.Type.SERVER, SignalRadarConfig.SPEC);
        ModComponents.COMPONENTS.register(modBus);
        it.ratlab.signalradar.registry.ModAttachments.ATTACHMENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        it.ratlab.signalradar.registry.ModSounds.SOUNDS.register(modBus);
        it.ratlab.signalradar.registry.ModRecipes.SERIALIZERS.register(modBus);
        it.ratlab.signalradar.registry.ModRecipes.CONDITIONS.register(modBus);
        it.ratlab.signalradar.registry.ModMenus.MENUS.register(modBus);
        modBus.addListener(it.ratlab.signalradar.addon.AddonRegistry::onRegister);
        modBus.addListener(SignalRadar::registerCapabilities);
        modBus.addListener(SignalRadar::addToTabs);
        modBus.addListener(it.ratlab.signalradar.net.RadarNetworking::register);
        NeoForge.EVENT_BUS.addListener(it.ratlab.signalradar.target.TargetManager::onAddReloadListener);
        NeoForge.EVENT_BUS.addListener(it.ratlab.signalradar.addon.detect.OreColorResolver::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(it.ratlab.signalradar.command.RadarCommands::register);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> it.ratlab.signalradar.scan.ScanHandler.reset());
        it.ratlab.signalradar.scan.ScanHandler.register(NeoForge.EVENT_BUS);
        it.ratlab.signalradar.progress.FoundHandler.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(it.ratlab.signalradar.recipe.RadarUpgradeRecipe::onItemCrafted);
        if (net.neoforged.fml.ModList.get().isLoaded("lootr")) {
            it.ratlab.signalradar.compat.lootr.LootrEvents.register(NeoForge.EVENT_BUS);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            it.ratlab.signalradar.progress.StageHelper.enableKubeJS();
            enableKubeJSEvents();
        }

        if (Boolean.getBoolean("signalradar.gametests")) {
            it.ratlab.signalradar.test.RadarGameTests.register(modBus);
            it.ratlab.signalradar.test.ScanGameTests.register(modBus);
            it.ratlab.signalradar.test.AddonGameTests.register(modBus);
            it.ratlab.signalradar.test.ProgressGameTests.register(modBus);
            it.ratlab.signalradar.test.CompatGameTests.register(modBus);
            it.ratlab.signalradar.test.EventGameTests.register(modBus);
            it.ratlab.signalradar.test.KubeJSGameTests.register(modBus);
        }

        if (FMLEnvironment.dist.isClient()) {
            it.ratlab.signalradar.client.SignalRadarClient.init(modBus, container);
        }
    }

    /** KubeJS events, binding bridge and startup addon registration; classes only touched when KubeJS is loaded. */
    private static void enableKubeJSEvents() {
        try {
            it.ratlab.signalradar.compat.kubejs.KubeJSCompat.init();
        } catch (Throwable t) {
            LOGGER.error("KubeJS event integration failed to load", t);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new RadarEnergyStorage(stack), ModItems.RADAR.get());
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.RADAR.get());
            ModItems.MODULES.forEach(m -> event.accept(m.get()));
            AddonRegistry.active().forEach(d -> AddonRegistry.item(d.id()).ifPresent(event::accept));
        }
    }
}
