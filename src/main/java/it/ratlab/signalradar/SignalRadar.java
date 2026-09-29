// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import com.mojang.logging.LogUtils;
import it.ratlab.signalradar.item.RadarEnergyStorage;
import it.ratlab.signalradar.registry.ModComponents;
import it.ratlab.signalradar.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(SignalRadar.MOD_ID)
public final class SignalRadar {
    public static final String MOD_ID = "signalradar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SignalRadar(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SignalRadarConfig.SPEC);
        ModComponents.COMPONENTS.register(modBus);
        ModItems.ITEMS.register(modBus);
        it.ratlab.signalradar.registry.ModRecipes.SERIALIZERS.register(modBus);
        modBus.addListener(SignalRadar::registerCapabilities);
        modBus.addListener(SignalRadar::addToTabs);

        if (Boolean.getBoolean("signalradar.gametests")) {
            it.ratlab.signalradar.test.RadarGameTests.register(modBus);
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
        }
    }
}
