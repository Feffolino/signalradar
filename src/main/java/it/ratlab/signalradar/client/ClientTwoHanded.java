// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.item.TwoHanded;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Client side of {@link TwoHanded}: also honours the main hand item's own two-handed arm pose (modded guns). */
public final class ClientTwoHanded {
    private ClientTwoHanded() {}

    /** An offhand radar is off: the main hand is two-handed by rule, or its client arm pose is two-handed. */
    public static boolean blocksOffhand(LocalPlayer player) {
        if (TwoHanded.blocksOffhand(player)) {
            return true;
        }
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return false;
        }
        HumanoidModel.ArmPose pose = IClientItemExtensions.of(main).getArmPose(player, InteractionHand.MAIN_HAND, main);
        return pose != null && pose.isTwoHanded();
    }
}
