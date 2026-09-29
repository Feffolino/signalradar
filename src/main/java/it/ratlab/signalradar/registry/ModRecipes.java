// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.recipe.RadarUpgradeRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SignalRadar.MOD_ID);

    public static final Supplier<RecipeSerializer<RadarUpgradeRecipe>> RADAR_UPGRADE = SERIALIZERS.register("radar_upgrade",
            () -> new SimpleCraftingRecipeSerializer<>(RadarUpgradeRecipe::new));

    private ModRecipes() {}
}
