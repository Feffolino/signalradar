// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.ratlab.signalradar.display.HandMath;
import it.ratlab.signalradar.item.RadarItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Radar client hooks: the item renderer, and the raise-to-face first-person pose. While raised (and while easing in
 * or out) the item moves from vanilla's rest position toward the screen centre, closer and larger, turned to face the
 * camera. Applied before the model's {@code firstperson_*} display transform.
 */
public final class RadarClientExtensions implements IClientItemExtensions {
    // Vanilla rest pose (ItemInHandRenderer.applyItemArmTransform) and the raised target.
    private static final float REST_X = 0.56f;
    private static final float REST_Y = -0.52f;
    private static final float REST_Z = -0.72f;
    private static final float RAISED_X = 0.14f;
    private static final float RAISED_Y = -0.36f;
    private static final float RAISED_Z = -0.56f;
    private static final float RAISED_SCALE = 1.3f;
    /** Cancels most of the display transform's yaw (firstperson_righthand rotation y = -12) so the screen faces you. */
    private static final float RAISED_YAW = 12f;
    private static final float RAISED_PITCH = -6f;

    private BlockEntityWithoutLevelRenderer renderer;

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            renderer = new RadarItemRenderer();
        }
        return renderer;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        if (!(stack.getItem() instanceof RadarItem) || arm != RaiseState.arm(player)) {
            return false;
        }
        float t = RaiseState.progress(partialTick);
        if (t <= 0f) {
            return false;
        }
        int side = HandMath.side(arm == HumanoidArm.LEFT);
        float restY = REST_Y + equipProcess * -0.6f;
        ps.translate(HandMath.raisedX(side, REST_X, RAISED_X, t), restY + (RAISED_Y - restY) * t, REST_Z + (RAISED_Z - REST_Z) * t);
        ps.mulPose(Axis.YP.rotationDegrees(HandMath.raisedYaw(side, RAISED_YAW, t)));
        ps.mulPose(Axis.XP.rotationDegrees(RAISED_PITCH * t));
        float s = 1f + (RAISED_SCALE - 1f) * t;
        ps.scale(s, s, s);
        return true;
    }
}
