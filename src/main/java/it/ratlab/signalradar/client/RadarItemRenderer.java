// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.item.RadarItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Radar item renderer (item model {@code radar.json} has parent {@code builtin/entity}): the tier's body model
 * ({@code models/item/radar_body_tN.json}, standalone), then the display and LED ({@link RadarDisplay}). The pose is
 * already in item model space with the model's display transform applied.
 */
public final class RadarItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation[] BODIES = new ModelResourceLocation[RadarItem.MAX_TIER + 1];

    static {
        for (int t = 0; t <= RadarItem.MAX_TIER; t++) {
            BODIES[t] = ModelResourceLocation.standalone(SignalRadar.id("item/radar_body_t" + t));
        }
    }

    public RadarItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation body : BODIES) {
            event.register(body);
        }
    }

    public static ModelResourceLocation body(int tier) {
        return BODIES[Math.max(0, Math.min(RadarItem.MAX_TIER, tier))];
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(body(RadarItem.tier(stack)));
        ItemRenderer items = mc.getItemRenderer();
        for (RenderType rt : model.getRenderTypes(stack, true)) {
            items.renderModelLists(model, stack, light, overlay, ps, ItemRenderer.getFoilBufferDirect(buffers, rt, true, stack.hasFoil()));
        }
        RadarDisplay.render(stack, ctx, ps, buffers);
    }
}
