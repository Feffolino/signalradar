// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import it.ratlab.signalradar.icon.IconSpec;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What an addon item detects. Built-ins and custom addons (KubeJS) are described by the same record.
 */
public record AddonDefinition(ResourceLocation id, Detector detector, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                              int color, int energyCost, String category, @Nullable String requiredModId,
                              @Nullable ResourceLocation tag, boolean useMapColor, @Nullable String icon, boolean stackable,
                              ResourceLocation texture) {

    /** Definition with the default texture id. */
    public AddonDefinition(ResourceLocation id, Detector detector, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                           int color, int energyCost, String category, @Nullable String requiredModId,
                           @Nullable ResourceLocation tag, boolean useMapColor, @Nullable String icon, boolean stackable) {
        this(id, detector, minTier, radiusMin, radiusMax, refreshSeconds, color, energyCost, category, requiredModId, tag, useMapColor,
                icon, stackable, null);
    }

    /** Definition without the stackable flag (every addon except the battery). */
    public AddonDefinition(ResourceLocation id, Detector detector, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                           int color, int energyCost, String category, @Nullable String requiredModId,
                           @Nullable ResourceLocation tag, boolean useMapColor, @Nullable String icon) {
        this(id, detector, minTier, radiusMin, radiusMax, refreshSeconds, color, energyCost, category, requiredModId, tag, useMapColor,
                icon, false);
    }

    /** Built-in style definition without an icon override. */
    public AddonDefinition(ResourceLocation id, Detector detector, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                           int color, int energyCost, String category, @Nullable String requiredModId,
                           @Nullable ResourceLocation tag, boolean useMapColor) {
        this(id, detector, minTier, radiusMin, radiusMax, refreshSeconds, color, energyCost, category, requiredModId, tag, useMapColor, null);
    }

    /** Detector types. The first four are public (custom addons); the rest are built-in behaviours (biosign, motion, compat). */
    public enum Detector {
        CONTAINER("container", true),
        BLOCK_TAG("block_tag", true),
        ENTITY_TAG("entity_tag", true),
        STRUCTURE_TAG("structure_tag", true),
        BIOSIGN("biosign", false),
        MOTION("motion", false),
        /** Compat detectors: need their mod, code lives in {@code compat/}. */
        MANHOLE("manhole", false),
        LOOTR("lootr", false),
        TEAM("team", false),
        /** Detects nothing (no hits, no blips); the addon does something else (battery: extra FE capacity). */
        NONE("none", false);

        private final String jsonName;
        private final boolean publicType;

        Detector(String jsonName, boolean publicType) {
            this.jsonName = jsonName;
            this.publicType = publicType;
        }

        public String jsonName() {
            return jsonName;
        }

        /** Parses a public detector name ({@code block_tag}, ...); null when unknown or not public. */
        @Nullable
        public static Detector parsePublic(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            for (Detector d : values()) {
                if (d.publicType && d.jsonName.equals(n)) {
                    return d;
                }
            }
            return null;
        }
    }

    public AddonDefinition {
        if (minTier < 0 || minTier > AddonMath.MAX_TIER) {
            throw new IllegalArgumentException("minTier must be 0..4: " + minTier);
        }
        if (radiusMin < 0 || radiusMax < radiusMin) {
            throw new IllegalArgumentException("radius must satisfy 0 <= min <= max: " + radiusMin + ".." + radiusMax);
        }
        if (refreshSeconds < 1) {
            throw new IllegalArgumentException("refreshSeconds must be >= 1: " + refreshSeconds);
        }
        if (energyCost < 0) {
            throw new IllegalArgumentException("energyCost must be >= 0: " + energyCost);
        }
        if (tag == null && (detector == Detector.BLOCK_TAG || detector == Detector.ENTITY_TAG || detector == Detector.STRUCTURE_TAG)) {
            throw new IllegalArgumentException("detector " + detector.jsonName + " needs a tag");
        }
        category = category == null || category.isBlank() ? id.getPath() : category;
        color &= 0xFFFFFF;
        icon = icon == null || icon.isBlank() ? null : IconSpec.normalize(icon, "");
        texture = texture == null ? defaultTexture(id) : texture;
    }

    /** {@code <addon ns>:item/<addon path>}. */
    public static ResourceLocation defaultTexture(ResourceLocation id) {
        return new ResourceLocation(id.getNamespace(), "item/" + id.getPath());
    }

    /** Starts a custom addon definition (public detector types only). Defaults: tier 0, radius 16..32, 5 s, white, 10 FE. */
    public static Builder builder(ResourceLocation id, Detector detector) {
        return new Builder(id, detector);
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final Detector detector;
        private int minTier;
        private int radiusMin = 16;
        private int radiusMax = 32;
        private int refresh = 5;
        private int color = 0xFFFFFF;
        private int energy = 10;
        private String category;
        private String requiredMod;
        private ResourceLocation tag;
        private String icon;
        private ResourceLocation texture;

        private Builder(ResourceLocation id, Detector detector) {
            this.id = id;
            this.detector = detector;
        }

        public Builder tag(ResourceLocation tag) {
            this.tag = tag;
            return this;
        }

        public Builder minTier(int minTier) {
            this.minTier = minTier;
            return this;
        }

        public Builder radius(int min, int max) {
            this.radiusMin = min;
            this.radiusMax = max;
            return this;
        }

        public Builder refresh(int seconds) {
            this.refresh = seconds;
            return this;
        }

        public Builder color(int rgb) {
            this.color = rgb;
            return this;
        }

        public Builder energy(int fe) {
            this.energy = fe;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /** Icon spec for every blip (see {@link IconSpec}); a bare id is an item. Null / blank = the detector default. */
        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        /** Item texture id {@code ns:item/path} (file {@code assets/ns/textures/item/path.png}); null = {@code <addon ns>:item/<addon path>}. */
        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder requiredMod(String modId) {
            this.requiredMod = modId;
            return this;
        }

        /** @throws IllegalArgumentException on invalid values */
        public AddonDefinition build() {
            return new AddonDefinition(id, detector, minTier, radiusMin, radiusMax, refresh, color, energy, category, requiredMod,
                    tag, false, icon, false, texture);
        }
    }
}
