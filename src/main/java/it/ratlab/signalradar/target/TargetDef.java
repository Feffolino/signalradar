// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.target;

import it.ratlab.signalradar.icon.IconSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A narrative target from {@code data/<ns>/signalradar/target/<id>.json}.
 */
public record TargetDef(
        ResourceLocation id,
        Component name,
        String category,
        int minTier,
        int color,
        int revealDistance,
        @Nullable String requiresStage,
        boolean requiresUnlock,
        int foundRadius,
        boolean hideWhenFound,
        Locator locator,
        String icon) {
    public static final int DEFAULT_REVEAL_DISTANCE = 128;
    public static final int DEFAULT_FOUND_RADIUS = 24;
    public static final int DEFAULT_COLOR = 0x7CFC00;

    /** Backwards compatible constructor: default icon. */
    public TargetDef(ResourceLocation id, Component name, String category, int minTier, int color, int revealDistance,
                     @Nullable String requiresStage, boolean requiresUnlock, int foundRadius, boolean hideWhenFound, Locator locator) {
        this(id, name, category, minTier, color, revealDistance, requiresStage, requiresUnlock, foundRadius, hideWhenFound, locator,
                IconSpec.DEFAULT_TARGET);
    }

    /** Blip id sent to the client. */
    public String blipId() {
        return id.toString();
    }
}
