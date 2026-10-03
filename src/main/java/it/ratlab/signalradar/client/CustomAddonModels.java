// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;

/**
 * Custom addons (KubeJS startup scripts, other mods) may ship no item model. {@link CustomAddonPack} gives each one an
 * {@code item/generated} model of its texture ({@code .texture()}, default {@code <ns>:item/<path>}). After baking, every custom
 * addon without an own model whose texture PNG does not exist in the loaded resources is pointed at the baked generic
 * {@code signalradar:item/addon_custom} model instead of the purple missing one. Layer 1 of that model (tint index 1) is tinted
 * with the addon colour, only for the addons that use the generic model.
 */
final class CustomAddonModels {
    static final ModelResourceLocation GENERIC = new ModelResourceLocation(SignalRadar.id("addon_custom"), "inventory");

    private CustomAddonModels() {}

    static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(GENERIC);
    }

    /** Ids pointed at the generic model in the last bake; the tint is only registered for these. */
    private static final java.util.Set<ResourceLocation> GENERIC_USERS = new java.util.HashSet<>();

    private static List<AddonDefinition> customs() {
        return AddonRegistry.active().stream().filter(d -> !AddonRegistry.isBuiltin(d)).toList();
    }

    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        BakedModel generic = models.get(GENERIC);
        if (generic == null) {
            SignalRadar.LOGGER.error("Generic custom addon model {} did not bake", GENERIC);
            return;
        }
        GENERIC_USERS.clear();
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        for (AddonDefinition def : customs()) {
            ResourceLocation id = def.id();
            ResourceLocation file = new ResourceLocation(id.getNamespace(), "models/item/" + id.getPath() + ".json");
            Optional<Resource> model = rm.getResource(file);
            if (model.isPresent() && !CustomAddonPack.PACK_ID.equals(model.get().sourcePackId())) {
                SignalRadar.LOGGER.info("Custom addon {}: own model {}", id, file);
                continue;
            }
            ResourceLocation tex = def.texture();
            ResourceLocation png = new ResourceLocation(tex.getNamespace(), "textures/" + tex.getPath() + ".png");
            if (model.isPresent() && rm.getResource(png).isPresent()) {
                SignalRadar.LOGGER.info("Custom addon {}: texture {}", id, tex);
            } else {
                models.put(new ModelResourceLocation(id, "inventory"), generic);
                GENERIC_USERS.add(id);
                SignalRadar.LOGGER.info("Custom addon {}: generic (texture missing)", id);
            }
        }
    }

    static void registerColors(RegisterColorHandlersEvent.Item event) {
        for (AddonDefinition def : customs()) {
            if (!GENERIC_USERS.contains(def.id())) {
                continue;
            }
            AddonRegistry.item(def.id()).ifPresent((Item item) -> {
                int argb = 0xFF000000 | def.color();
                event.register((stack, tintIndex) -> tintIndex == 1 ? argb : -1, item);
            });
        }
    }
}
