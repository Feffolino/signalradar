// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What an addon item detects. Built-ins and custom addons (KubeJS, phase 7) are described by the same record.
 *
 * @param id             item id (any namespace)
 * @param detector       detector type
 * @param minTier        radar tier needed to install it (0..4)
 * @param radiusMin      radius in blocks at {@code minTier}; 0 together with {@code radiusMax} 0 = the radar's tier range
 * @param radiusMax      radius in blocks at tier 4
 * @param refreshSeconds seconds between two detections (results are cached until then)
 * @param color          0xRRGGBB blip colour (fallback for ore blips without a material tag)
 * @param energyCost     FE added to every base scan charge while installed
 * @param category       blip category text (drawn by colour; {@code motion} pulses and beeps)
 * @param requiredModId  item is only registered when this mod is loaded (null = always)
 * @param tag            block / entity / structure tag id for the tag based detectors (null = detector default)
 * @param useMapColor    block blips take the colour of their {@code c:ores/<material>} tag instead of {@code color} (ore)
 */
public record AddonDefinition(ResourceLocation id, Detector detector, int minTier, int radiusMin, int radiusMax, int refreshSeconds,
                              int color, int energyCost, String category, @Nullable String requiredModId,
                              @Nullable ResourceLocation tag, boolean useMapColor) {

    /** Detector types. The first four are public (custom addons); biosign and motion are built-in behaviours. */
    public enum Detector {
        CONTAINER("container", true),
        BLOCK_TAG("block_tag", true),
        ENTITY_TAG("entity_tag", true),
        STRUCTURE_TAG("structure_tag", true),
        BIOSIGN("biosign", false),
        MOTION("motion", false);

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

        public Builder requiredMod(String modId) {
            this.requiredMod = modId;
            return this;
        }

        /** @throws IllegalArgumentException on invalid values */
        public AddonDefinition build() {
            return new AddonDefinition(id, detector, minTier, radiusMin, radiusMax, refresh, color, energy, category, requiredMod,
                    tag, false);
        }
    }
}
