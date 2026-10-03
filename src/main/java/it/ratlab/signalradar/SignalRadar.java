// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import com.mojang.logging.LogUtils;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.recipe.DefaultRecipesCondition;
import it.ratlab.signalradar.recipe.RadarUpgradeRecipe;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.registry.ModMenus;
import it.ratlab.signalradar.registry.ModRecipes;
import it.ratlab.signalradar.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SignalRadar.MOD_ID)
public final class SignalRadar {
    public static final String MOD_ID = "signalradar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SignalRadar() {
        SignalRadarStartupConfig.load();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SignalRadarConfig.SPEC);

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModMenus.MENUS.register(modBus);

        CraftingHelper.register(DefaultRecipesCondition.Serializer.INSTANCE);

        modBus.addListener(AddonRegistry::onRegister);
        modBus.addListener(SignalRadar::addToTabs);

        it.ratlab.signalradar.net.RadarNetworking.register();

        MinecraftForge.EVENT_BUS.addListener(RadarUpgradeRecipe::onItemCrafted);
        MinecraftForge.EVENT_BUS.addListener(it.ratlab.signalradar.target.TargetManager::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(it.ratlab.signalradar.addon.detect.OreColorResolver::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(it.ratlab.signalradar.command.RadarCommands::register);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) -> it.ratlab.signalradar.scan.ScanHandler.reset());
        it.ratlab.signalradar.scan.ScanHandler.register(MinecraftForge.EVENT_BUS);
        it.ratlab.signalradar.progress.FoundHandler.register(MinecraftForge.EVENT_BUS);
        if (net.minecraftforge.fml.ModList.get().isLoaded("lootr")) {
            it.ratlab.signalradar.compat.lootr.LootrEvents.register(MinecraftForge.EVENT_BUS);
        }
        if (net.minecraftforge.fml.ModList.get().isLoaded("kubejs")) {
            it.ratlab.signalradar.progress.StageHelper.enableKubeJS();
            enableKubeJSEvents();
        }
        if (Boolean.getBoolean("signalradar.gametests")) {
            it.ratlab.signalradar.test.RadarGameTests.register(modBus);
            it.ratlab.signalradar.test.ScanGameTests.register(modBus);
            it.ratlab.signalradar.test.AddonGameTests.register(modBus);
            it.ratlab.signalradar.test.BatteryGameTests.register(modBus);
            it.ratlab.signalradar.test.ProgressGameTests.register(modBus);
            it.ratlab.signalradar.test.CompatGameTests.register(modBus);
            it.ratlab.signalradar.test.EventGameTests.register(modBus);
            it.ratlab.signalradar.test.KubeJSGameTests.register(modBus);
        }

        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> it.ratlab.signalradar.client.SignalRadarClient.init(modBus));
    }

    private static void enableKubeJSEvents() {
        try {
            it.ratlab.signalradar.compat.kubejs.KubeJSCompat.init();
        } catch (Throwable t) {
            LOGGER.error("KubeJS event integration failed to load", t);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.RADAR.get());
            ModItems.MODULES.forEach(m -> event.accept(m.get()));
            AddonRegistry.active().forEach(d -> AddonRegistry.item(d.id()).ifPresent(event::accept));
        }
    }
}
