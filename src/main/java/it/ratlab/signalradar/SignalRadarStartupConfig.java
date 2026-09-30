// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Startup config: {@code config/signalradar-startup.toml}. FML loads a STARTUP config as soon as it is registered (in the
 * mod constructor), so it is readable before anything else. Changes need a game restart.
 */
public final class SignalRadarStartupConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.push("recipes"); }
    public static final ModConfigSpec.BooleanValue ENABLE_DEFAULT_RECIPES = B
            .comment("Load the mod's default crafting recipes (radar, modules 1-4, addons; vanilla items only). They use the",
                    "NeoForge condition 'signalradar:default_recipes_enabled'. Packs can disable or remove the",
                    "'signalradar:default/*' recipes (this switch, a datapack, or KubeJS event.remove).")
            .define("enableDefaultRecipes", true);
    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private SignalRadarStartupConfig() {}

    public static boolean defaultRecipesEnabled() {
        return SPEC.isLoaded() ? ENABLE_DEFAULT_RECIPES.getAsBoolean() : ENABLE_DEFAULT_RECIPES.getDefault();
    }
}
