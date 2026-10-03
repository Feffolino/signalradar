// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Startup config: {@code config/signalradar-startup.toml}. Read manually on startup before recipes are evaluated.
 */
public final class SignalRadarStartupConfig {
    private static boolean defaultRecipes = true;

    private SignalRadarStartupConfig() {}

    public static void load() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("signalradar-startup.toml");
            CommentedFileConfig config = CommentedFileConfig.builder(path)
                    .sync()
                    .autosave()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            if (!path.toFile().exists()) {
                config.set("recipes.enableDefaultRecipes", true);
                config.setComment("recipes", "Load the mod's default crafting recipes (radar, modules 1-4, addons; vanilla items only).\n"
                        + "They use the Forge condition 'signalradar:default_recipes_enabled'. Packs can disable or remove the\n"
                        + "'signalradar:default/*' recipes (this switch, a datapack, or KubeJS event.remove).");
                config.save();
            } else {
                config.load();
                Object val = config.get("recipes.enableDefaultRecipes");
                if (val instanceof Boolean b) {
                    defaultRecipes = b;
                }
            }
            config.close();
        } catch (Exception e) {
            SignalRadar.LOGGER.warn("Failed to read signalradar-startup.toml, using default true", e);
        }
    }

    public static boolean defaultRecipesEnabled() {
        return defaultRecipes;
    }
}
