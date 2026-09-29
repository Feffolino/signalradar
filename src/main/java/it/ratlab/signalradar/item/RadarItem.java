// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import it.ratlab.signalradar.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class RadarItem extends Item {
    public static final int MAX_TIER = 4;
    private static final int BAR_COLOR = 0x39FF5A;

    public RadarItem(Properties properties) {
        super(properties);
    }

    public static int tier(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModComponents.TIER.get(), 0), 0, MAX_TIER);
    }

    public static void setTier(ItemStack stack, int tier) {
        stack.set(ModComponents.TIER.get(), Mth.clamp(tier, 0, MAX_TIER));
    }

    public static int energy(ItemStack stack) {
        return Mth.clamp(stack.getOrDefault(ModComponents.ENERGY.get(), 0), 0, SignalRadarConfig.capacity());
    }

    public static void setEnergy(ItemStack stack, int fe) {
        stack.set(ModComponents.ENERGY.get(), Mth.clamp(fe, 0, SignalRadarConfig.capacity()));
    }

    /** Raw installed addon ids (slot order). */
    public static List<ResourceLocation> addons(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ADDONS.get(), List.of());
    }

    public static void setAddons(ItemStack stack, List<ResourceLocation> ids) {
        if (ids.isEmpty()) {
            stack.remove(ModComponents.ADDONS.get());
        } else {
            stack.set(ModComponents.ADDONS.get(), List.copyOf(ids));
        }
    }

    /** Addon slots of a radar of this tier ({@code slotsByTier} config, default tier + 1, max 5). */
    public static int slots(int tier) {
        return SignalRadarConfig.slotsByTier()[Mth.clamp(tier, 0, MAX_TIER)];
    }

    /**
     * Hold right-click = raise to face (client pose + text line; vanilla slows a player using an item). Sneaking
     * opens the addon menu instead.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) {
                AddonMenu.open(sp, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** NONE: our own hand transform draws the raise (spyglass would hide the item and zoom). */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Energy / tier components change on every scan: only a real item or slot change re-raises the item. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / SignalRadarConfig.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.signalradar.tier", tier(stack), MAX_TIER).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.signalradar.energy", energy(stack), SignalRadarConfig.capacity())
                .withStyle(ChatFormatting.GRAY));
        List<ResourceLocation> ids = AddonRules.installed(stack);
        tooltip.add(Component.translatable("tooltip.signalradar.addons", ids.size(), slots(tier(stack))).withStyle(ChatFormatting.GRAY));
        for (ResourceLocation id : ids) {
            tooltip.add(Component.literal(" ").append(AddonRegistry.item(id).map(i -> i.getDescription())
                    .orElse(Component.literal(id.toString()))).withStyle(ChatFormatting.DARK_GREEN));
        }
    }
}
