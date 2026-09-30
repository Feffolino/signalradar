// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeStartupEvent;
import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.api.RadarAddonChangedEvent;
import it.ratlab.signalradar.api.RadarScanEvent;
import it.ratlab.signalradar.api.RadarTargetFoundEvent;
import it.ratlab.signalradar.api.RadarUpgradedEvent;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.target.TargetDef;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Event objects of the {@code SignalRadarEvents} group. */
public final class SignalRadarKubeEvents {
    private SignalRadarKubeEvents() {}

    /** Ids without a namespace get {@code kubejs:}; a leading {@code #} (tags) is ignored. */
    static ResourceLocation rl(String id) {
        String s = id.startsWith("#") ? id.substring(1) : id;
        return ResourceLocation.parse(s.contains(":") ? s : "kubejs:" + s);
    }

    /** A number (0xRRGGBB) or a hex string ({@code '#RRGGBB'}, {@code 'RRGGBB'}, {@code '0xRRGGBB'}). */
    static int color(Object o) {
        if (o instanceof Number n) {
            return n.intValue() & 0xFFFFFF;
        }
        String s = String.valueOf(o).trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a colour (use 0xRRGGBB or '#RRGGBB'): " + o);
        }
    }

    static Component text(Object o) {
        return o instanceof Component c ? c : Component.literal(String.valueOf(o));
    }

    @Nullable
    static String str(@Nullable ResourceLocation id) {
        return id == null ? null : id.toString();
    }

    // ------------------------------------------------------------------ startup

    /** {@code SignalRadarEvents.registerAddons(e => e.create('ns:id', 'block_tag').tag('#c:ores/x')...)}. */
    public static final class RegisterAddons implements KubeStartupEvent {
        private final List<AddonBuilderJS> builders = new ArrayList<>();

        /**
         * New custom addon bound to a public detector type: {@code container}, {@code block_tag}, {@code entity_tag},
         * {@code structure_tag}. Registered after all listeners ran; invalid values are reported in the startup log.
         */
        public AddonBuilderJS create(String id, String detector) {
            AddonDefinition.Detector d = AddonDefinition.Detector.parsePublic(detector);
            if (d == null) {
                throw new IllegalArgumentException("Unknown detector '" + detector + "' (container, block_tag, entity_tag, structure_tag)");
            }
            AddonBuilderJS b = new AddonBuilderJS(rl(id), d);
            builders.add(b);
            return b;
        }

        List<AddonBuilderJS> builders() {
            return builders;
        }
    }

    /** Builder returned by {@code create}; every setter returns the builder. */
    public static final class AddonBuilderJS {
        private final ResourceLocation id;
        private final AddonDefinition.Builder builder;

        AddonBuilderJS(ResourceLocation id, AddonDefinition.Detector detector) {
            this.id = id;
            this.builder = AddonDefinition.builder(id, detector);
        }

        ResourceLocation id() {
            return id;
        }

        /** Block / entity / structure tag, with or without {@code #}. Required for the tag detectors. */
        public AddonBuilderJS tag(String tag) {
            builder.tag(rl(tag));
            return this;
        }

        public AddonBuilderJS minTier(int tier) {
            builder.minTier(tier);
            return this;
        }

        /** Radius in blocks at minTier and at tier 4 (0, 0 = the radar's range). */
        public AddonBuilderJS radius(int min, int max) {
            builder.radius(min, max);
            return this;
        }

        public AddonBuilderJS refresh(int seconds) {
            builder.refresh(seconds);
            return this;
        }

        public AddonBuilderJS color(Object color) {
            builder.color(SignalRadarKubeEvents.color(color));
            return this;
        }

        /** FE added to every base scan while installed. */
        public AddonBuilderJS energy(int fe) {
            builder.energy(fe);
            return this;
        }

        public AddonBuilderJS category(String category) {
            builder.category(category);
            return this;
        }

        /**
         * Icon of every blip of this addon: {@code block:<id>}, {@code item:<id>}, {@code texture:<rl>}, {@code entity:<id>},
         * {@code player:<uuid>}; a bare id is an item. Without it the detector decides (block_tag: the block face, entity_tag:
         * the mob face or body, container: the block item, structure_tag: a map).
         */
        public AddonBuilderJS icon(String icon) {
            builder.icon(icon);
            return this;
        }

        /**
         * Item texture {@code 'ns:item/xxx'} = {@code kubejs/assets/ns/textures/item/xxx.png} (a leading {@code textures/} and a trailing
         * {@code .png} are accepted). Default {@code <addon ns>:item/<addon path>}. Client side only: without the PNG (and without
         * an own {@code models/item} json) the addon uses the generic tinted model.
         */
        public AddonBuilderJS texture(String texture) {
            String t = texture.endsWith(".png") ? texture.substring(0, texture.length() - 4) : texture;
            ResourceLocation rl = rl(t);
            String path = rl.getPath().startsWith("textures/") ? rl.getPath().substring("textures/".length()) : rl.getPath();
            builder.texture(ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), path));
            return this;
        }

        /** Only registered when this mod is loaded. */
        public AddonBuilderJS requiredMod(String modId) {
            builder.requiredMod(modId);
            return this;
        }

        AddonDefinition build() {
            return builder.build();
        }
    }

    // ------------------------------------------------------------------ server

    /** Read-only view of one blip for scripts. */
    public static final class ScanTargetJS {
        private final Blip blip;

        ScanTargetJS(Blip blip) {
            this.blip = blip;
        }

        /** Target id, {@code <addon id>/<hit>} for addon hits, {@code script/<name>} for added ones. */
        public String getId() {
            return blip.id();
        }

        public String getCategory() {
            return blip.category();
        }

        /** Shown name ({@code ???} while not revealed). */
        public String getName() {
            return blip.name().getString();
        }

        public int getColor() {
            return blip.color();
        }

        /** Icon spec (empty = dot). */
        public String getIcon() {
            return blip.icon();
        }

        /** Shown (fuzzed) position. */
        public double getX() {
            return blip.x();
        }

        public double getY() {
            return blip.y();
        }

        public double getZ() {
            return blip.z();
        }

        public boolean isOutOfRange() {
            return blip.outOfRange();
        }

        public boolean isFound() {
            return blip.found();
        }

        @Override
        public String toString() {
            return blip.id();
        }
    }

    /**
     * {@code SignalRadarEvents.scan}: player, radar, tier, range, targets. Remove with {@code removeTarget(id)},
     * {@code removeCategory(cat)}, {@code removeIf(t => ...)}; add with {@code addTarget(name, color, x, y, z)};
     * {@code cancel()} = NO SIGNAL for this scan.
     */
    public static final class Scan implements KubePlayerEvent {
        private final RadarScanEvent event;

        Scan(RadarScanEvent event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.getPlayer();
        }

        public ItemStack getRadar() {
            return event.getRadar();
        }

        public int getTier() {
            return event.getTier();
        }

        public int getRange() {
            return event.getRange();
        }

        /** Current targets (a copy; use the remove / add methods to change them). */
        public List<ScanTargetJS> getTargets() {
            List<ScanTargetJS> out = new ArrayList<>();
            for (Blip b : event.getTargets()) {
                out.add(new ScanTargetJS(b));
            }
            return out;
        }

        public List<String> getTargetIds() {
            List<String> out = new ArrayList<>();
            for (Blip b : event.getTargets()) {
                out.add(b.id());
            }
            return out;
        }

        /** Removes the target with this id, or all hits of this addon id. Returns the number removed. */
        public int removeTarget(String id) {
            return event.removeTarget(id);
        }

        public int removeCategory(String category) {
            return event.removeCategory(category);
        }

        public int removeIf(Predicate<ScanTargetJS> filter) {
            return event.removeTargets(b -> filter.test(new ScanTargetJS(b)));
        }

        /** Adds an exact (unfuzzed) blip; name is text or a text component, colour 0xRRGGBB or '#RRGGBB'. */
        public ScanTargetJS addTarget(Object name, Object color, double x, double y, double z) {
            return new ScanTargetJS(event.addTarget(text(name), SignalRadarKubeEvents.color(color), x, y, z));
        }
    }

    /** {@code SignalRadarEvents.targetFound}: player, targetId, target, targetName. */
    public static final class TargetFound implements KubePlayerEvent {
        private final RadarTargetFoundEvent event;

        TargetFound(RadarTargetFoundEvent event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.getPlayer();
        }

        public String getTargetId() {
            return event.getTargetId().toString();
        }

        public TargetDef getTarget() {
            return event.getTarget();
        }

        public String getTargetName() {
            return event.getTarget().name().getString();
        }
    }

    /** {@code SignalRadarEvents.upgraded}: player, radar, oldTier, newTier. */
    public static final class Upgraded implements KubePlayerEvent {
        private final RadarUpgradedEvent event;

        Upgraded(RadarUpgradedEvent event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.getPlayer();
        }

        public ItemStack getRadar() {
            return event.getRadar();
        }

        public int getOldTier() {
            return event.getOldTier();
        }

        public int getNewTier() {
            return event.getNewTier();
        }
    }

    /** {@code SignalRadarEvents.addonChanged}: player, radar, slot, oldAddon, newAddon (ids, null = empty slot). */
    public static final class AddonChanged implements KubePlayerEvent {
        private final RadarAddonChangedEvent event;

        AddonChanged(RadarAddonChangedEvent event) {
            this.event = event;
        }

        @Override
        public Player getEntity() {
            return event.getPlayer();
        }

        public ItemStack getRadar() {
            return event.getRadar();
        }

        public int getSlot() {
            return event.getSlot();
        }

        @Nullable
        public String getOldAddon() {
            return str(event.getOldAddon());
        }

        @Nullable
        public String getNewAddon() {
            return str(event.getNewAddon());
        }
    }
}
