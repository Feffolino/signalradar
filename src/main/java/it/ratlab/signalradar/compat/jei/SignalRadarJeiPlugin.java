// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.jei;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * JEI information pages for the radar, the tier modules and every active addon (built-in and custom). JEI finds this
 * class through {@link JeiPlugin} only when JEI is installed; nothing else in the mod references it.
 */
@JeiPlugin
public final class SignalRadarJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = SignalRadar.id("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(ModItems.RADAR.get(),
                Component.translatable("jei.signalradar.radar.1"),
                Component.translatable("jei.signalradar.radar.2"),
                Component.translatable("jei.signalradar.radar.3"));
        for (var module : ModItems.MODULES) {
            registration.addIngredientInfo(module.get(), Component.translatable("jei.signalradar.module"));
        }
        for (AddonDefinition def : AddonRegistry.active()) {
            AddonRegistry.item(def.id()).ifPresent(item -> {
                List<Component> lines = new ArrayList<>();
                item.appendHoverText(new ItemStack(item), Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
                lines.add(Component.translatable("jei.signalradar.addon.install"));
                registration.addIngredientInfo(item, lines.toArray(new Component[0]));
            });
        }
    }
}
