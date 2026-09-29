// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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
    }
}
