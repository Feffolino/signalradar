// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.display.RadarMath;
import it.ratlab.signalradar.item.RadarItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** Local player's raise-to-face progress (0 = at rest, 1 = raised), eased in and out over {@link #TICKS} ticks. */
public final class RaiseState {
    public static final float TICKS = 5f;

    private static float prev;
    private static float now;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;

    private RaiseState() {}

    static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        boolean using = p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof RadarItem;
        if (using) {
            hand = p.getUsedItemHand();
        }
        if (p == null || !(p.getItemInHand(hand).getItem() instanceof RadarItem)) {
            // Radar left the raised hand: snap back, never animate some other item.
            prev = now = 0f;
            return;
        }
        prev = now;
        now = Mth.clamp(now + (using ? 1f : -1f) / TICKS, 0f, 1f);
    }

    /** Eased progress for this frame. */
    public static float progress(float partialTick) {
        return RadarMath.ease(Mth.lerp(partialTick, prev, now));
    }

    public static InteractionHand hand() {
        return hand;
    }

    /** Arm of the raised hand for this player. */
    public static HumanoidArm arm(LocalPlayer player) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    static void reset() {
        prev = now = 0f;
        hand = InteractionHand.MAIN_HAND;
    }
}
