// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.detect;

import it.ratlab.signalradar.addon.AddonConfig;
import it.ratlab.signalradar.addon.OreColors;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Colour of an ore block from its {@code c:ores/<material>} sub-tag. The material of each block is cached and the cache
 * is cleared when tags reload; overrides from the config are applied at lookup time (parsed once per config value).
 */
public final class OreColorResolver {
    private static final Map<Block, Optional<String>> MATERIALS = new ConcurrentHashMap<>();
    private static volatile List<? extends String> parsedFrom;
    private static volatile Map<String, Integer> parsed = Map.of();

    private OreColorResolver() {}

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        clear();
    }

    public static void clear() {
        MATERIALS.clear();
    }

    /** Material name of the block ({@code iron}, ...), the alphabetically first one when several sub-tags match. */
    public static Optional<String> material(Block block) {
        return MATERIALS.computeIfAbsent(block, b -> b.builtInRegistryHolder().tags()
                .map(TagKey::location)
                .filter(l -> l.getNamespace().equals("c"))
                .map(l -> OreColors.materialOfTagPath(l.getPath()))
                .filter(m -> m != null)
                .sorted()
                .findFirst());
    }

    /** 0xRRGGBB for the block's material, or 0 when the block has no {@code c:ores/<material>} tag (caller falls back). */
    public static int color(Block block) {
        return material(block).map(m -> OreColors.colorOf(m, overrides())).orElse(0);
    }

    private static Map<String, Integer> overrides() {
        List<? extends String> cur = AddonConfig.oreColorOverrides();
        if (cur != parsedFrom) {
            parsed = OreColors.parseOverrides(cur);
            parsedFrom = cur;
        }
        return parsed;
    }
}
