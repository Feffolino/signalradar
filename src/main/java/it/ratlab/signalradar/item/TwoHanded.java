// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import it.ratlab.signalradar.SignalRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Two-handed main hand detection: an offhand radar is switched off while the main hand uses or holds something that
 * takes both hands. Safe on both sides (no client classes).
 */
public final class TwoHanded {
    /** Items in this tag count as two-handed whatever their use animation (guns of other mods). Ships empty. */
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, SignalRadar.id("two_handed"));

    private TwoHanded() {}

    /** Use animations that take both hands while the item is being used. */
    public static boolean isTwoHandedAnim(UseAnim anim) {
        return anim == UseAnim.BOW || anim == UseAnim.CROSSBOW || anim == UseAnim.SPEAR || anim == UseAnim.SPYGLASS
                || anim == UseAnim.BRUSH || anim == UseAnim.TOOT_HORN;
    }

    /** The main hand takes both hands right now, so an offhand radar must stay off. The main-hand radar is never affected. */
    public static boolean blocksOffhand(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return false;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && isTwoHandedAnim(main.getUseAnimation())) {
            return true;
        }
        return (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main)) || main.is(TAG);
    }
}
