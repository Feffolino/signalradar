// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.recipe;

import com.mojang.serialization.MapCodec;
import it.ratlab.signalradar.SignalRadarStartupConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * NeoForge load condition {@code {"type": "signalradar:default_recipes_enabled"}}: true while
 * {@code recipes.enableDefaultRecipes} (signalradar-startup.toml) is on. Evaluated when datapacks load.
 */
public final class DefaultRecipesCondition implements ICondition {
    public static final DefaultRecipesCondition INSTANCE = new DefaultRecipesCondition();
    public static final MapCodec<DefaultRecipesCondition> CODEC = MapCodec.unit(INSTANCE);

    private DefaultRecipesCondition() {}

    @Override
    public boolean test(IContext context) {
        return SignalRadarStartupConfig.defaultRecipesEnabled();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "signalradar:default_recipes_enabled";
    }
}
