// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.recipe.RadarUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SignalRadar.MOD_ID);

    public static final RegistryObject<RecipeSerializer<RadarUpgradeRecipe>> RADAR_UPGRADE = SERIALIZERS.register("radar_upgrade",
            () -> new SimpleCraftingRecipeSerializer<>(RadarUpgradeRecipe::new));

    private ModRecipes() {}
}
