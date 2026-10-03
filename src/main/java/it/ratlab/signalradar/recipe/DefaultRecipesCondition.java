// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.recipe;

import com.google.gson.JsonObject;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarStartupConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Forge load condition {@code {"type": "signalradar:default_recipes_enabled"}}: true while
 * {@code recipes.enableDefaultRecipes} is on.
 */
public final class DefaultRecipesCondition implements ICondition {
    public static final ResourceLocation ID = SignalRadar.id("default_recipes_enabled");
    public static final DefaultRecipesCondition INSTANCE = new DefaultRecipesCondition();

    private DefaultRecipesCondition() {}

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return SignalRadarStartupConfig.defaultRecipesEnabled();
    }

    @Override
    public String toString() {
        return "signalradar:default_recipes_enabled";
    }

    public static final class Serializer implements IConditionSerializer<DefaultRecipesCondition> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public void write(JsonObject json, DefaultRecipesCondition value) {}

        @Override
        public DefaultRecipesCondition read(JsonObject json) {
            return DefaultRecipesCondition.INSTANCE;
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
