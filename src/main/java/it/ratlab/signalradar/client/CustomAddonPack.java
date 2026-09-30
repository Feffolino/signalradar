// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory client resource pack (always on, lowest priority) that gives every custom addon an {@code item/generated} model
 * {@code <ns>:models/item/<path>.json} with {@code layer0} = the addon's texture (default {@code <ns>:item/<path>}, KubeJS:
 * {@code kubejs/assets/<ns>/textures/item/<path>.png}). Any real model in KubeJS or a resource pack has a higher priority and wins.
 * {@link CustomAddonModels} replaces the models whose texture does not exist by the generic tinted one.
 */
final class CustomAddonPack implements PackResources {
    static final String PACK_ID = "signalradar_addon_models";

    private final PackLocationInfo info;

    private CustomAddonPack(PackLocationInfo info) {
        this.info = info;
    }

    static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(PACK_ID, Component.literal("Signal Radar custom addon models"), PackSource.BUILT_IN,
                Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new CustomAddonPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new CustomAddonPack(location);
            }
        }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (pack == null) {
            SignalRadar.LOGGER.error("Custom addon model pack could not be created");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    /** Model path ({@code models/item/<path>.json}) to the addon definition, for the custom addons registered right now. */
    private static Map<String, AddonDefinition> models(String namespace) {
        Map<String, AddonDefinition> out = new HashMap<>();
        for (AddonDefinition def : AddonRegistry.active()) {
            if (!AddonRegistry.isBuiltin(def) && def.id().getNamespace().equals(namespace)) {
                out.put("models/item/" + def.id().getPath() + ".json", def);
            }
        }
        return out;
    }

    private static String modelJson(AddonDefinition def) {
        return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + def.texture() + "\"}}";
    }

    private static IoSupplier<InputStream> supplier(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }

    private static String packMeta() {
        return "{\"pack\":{\"description\":\"Signal Radar custom addon models\",\"pack_format\":"
                + SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES) + "}}";
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && PACK_META.equals(elements[0])) {
            return supplier(packMeta());
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        AddonDefinition def = models(location.getNamespace()).get(location.getPath());
        return def == null ? null : supplier(modelJson(def));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        for (Map.Entry<String, AddonDefinition> e : models(namespace).entrySet()) {
            if (e.getKey().equals(path) || e.getKey().startsWith(path + "/")) {
                output.accept(ResourceLocation.fromNamespaceAndPath(namespace, e.getKey()), supplier(modelJson(e.getValue())));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> out = new LinkedHashSet<>();
        if (type == PackType.CLIENT_RESOURCES) {
            for (AddonDefinition def : AddonRegistry.active()) {
                if (!AddonRegistry.isBuiltin(def)) {
                    out.add(def.id().getNamespace());
                }
            }
        }
        return out;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
        if (!"pack".equals(serializer.getMetadataSectionName())) {
            return null;
        }
        JsonObject root = JsonParser.parseString(packMeta()).getAsJsonObject();
        return serializer.fromJson(root.getAsJsonObject("pack"));
    }

    @Override
    public PackLocationInfo location() {
        return info;
    }

    @Override
    public void close() {}
}
