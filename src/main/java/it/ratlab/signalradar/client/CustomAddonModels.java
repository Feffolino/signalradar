// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Custom addons (KubeJS startup scripts, other mods) may ship no item model. Those items get the generic
 * {@code signalradar:item/addon_custom} model instead of the purple missing model: after baking, every custom addon
 * whose {@code assets/<ns>/models/item/<path>.json} does not exist in the loaded resources is pointed at the baked
 * generic model. Layer 1 of that model (tint index 1) is tinted with the addon colour for every custom addon.
 */
final class CustomAddonModels {
    static final ModelResourceLocation GENERIC = ModelResourceLocation.standalone(SignalRadar.id("item/addon_custom"));

    private CustomAddonModels() {}

    static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(GENERIC);
    }

    private static List<AddonDefinition> customs() {
        return AddonRegistry.active().stream().filter(d -> !AddonRegistry.isBuiltin(d)).toList();
    }

    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        BakedModel generic = models.get(GENERIC);
        if (generic == null) {
            SignalRadar.LOGGER.error("Generic custom addon model {} did not bake", GENERIC);
            return;
        }
        List<ResourceLocation> replaced = new ArrayList<>();
        for (AddonDefinition def : customs()) {
            ResourceLocation id = def.id();
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/item/" + id.getPath() + ".json");
            if (Minecraft.getInstance().getResourceManager().getResource(file).isEmpty()) {
                models.put(ModelResourceLocation.inventory(id), generic);
                replaced.add(id);
            }
        }
        if (!replaced.isEmpty()) {
            SignalRadar.LOGGER.info("Custom addons without an item model use the generic addon model: {}", replaced);
        }
    }

    static void registerColors(RegisterColorHandlersEvent.Item event) {
        for (AddonDefinition def : customs()) {
            AddonRegistry.item(def.id()).ifPresent((Item item) -> {
                int argb = FastColor.ARGB32.opaque(def.color());
                event.register((stack, tintIndex) -> tintIndex == 1 ? argb : -1, item);
            });
        }
    }
}
