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
