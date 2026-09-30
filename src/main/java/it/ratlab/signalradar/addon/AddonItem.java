// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An addon: goes into a radar slot; everything it does is described by its {@link AddonDefinition}. */
public class AddonItem extends Item {
    private final ResourceLocation defId;

    public AddonItem(ResourceLocation defId, Properties properties) {
        super(properties);
        this.defId = defId;
    }

    public ResourceLocation defId() {
        return defId;
    }

    public Optional<AddonDefinition> definition() {
        return AddonRegistry.get(defId);
    }

    /**
     * Custom addons without a lang entry show "Radar Addon (&lt;category&gt;)" instead of the raw key (resolved on the
     * client, so a KubeJS / resource pack translation wins).
     */
    @Override
    public Component getName(ItemStack stack) {
        Optional<AddonDefinition> def = definition();
        if (def.isPresent() && !AddonRegistry.isBuiltin(def.get())) {
            return Component.translatableWithFallback(getDescriptionId(stack), "Radar Addon (" + def.get().category() + ")");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Optional<AddonDefinition> opt = definition();
        if (opt.isEmpty()) {
            return;
        }
        AddonDefinition def = opt.get();
        AddonSettings s = AddonSettings.of(def);
        if (def.detector() == AddonDefinition.Detector.NONE) {
            // Battery: capacity instead of detection values.
            tooltip.add(Component.translatable("tooltip.signalradar.addon.addon_battery", AddonConfig.capacityPerBattery(),
                    stack.getMaxStackSize()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.signalradar.addon.battery_note").withStyle(ChatFormatting.DARK_GREEN));
            tooltip.add(Component.translatable("tooltip.signalradar.addon.min_tier", s.minTier()).withStyle(ChatFormatting.GREEN));
            if (!s.enabled()) {
                tooltip.add(Component.translatable("tooltip.signalradar.addon.disabled").withStyle(ChatFormatting.RED));
            }
            return;
        }
        String specific = "tooltip." + defId.getNamespace() + ".addon." + defId.getPath();
        String detects = Language.getInstance().has(specific) ? specific : "tooltip.signalradar.detector." + def.detector().jsonName();
        tooltip.add(Component.translatable(detects).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.signalradar.addon.min_tier", s.minTier()).withStyle(ChatFormatting.GREEN));
        if (s.usesTierRange()) {
            tooltip.add(Component.translatable("tooltip.signalradar.addon.range_tier").withStyle(ChatFormatting.DARK_GREEN));
        } else if (s.radiusMin() >= AddonMath.WHOLE_DIMENSION_RADIUS) {
            tooltip.add(Component.translatable("tooltip.signalradar.addon.range_dimension").withStyle(ChatFormatting.DARK_GREEN));
        } else {
            tooltip.add(Component.translatable("tooltip.signalradar.addon.range", s.radiusMin(), s.radiusMax()).withStyle(ChatFormatting.DARK_GREEN));
        }
        tooltip.add(Component.translatable("tooltip.signalradar.addon.refresh", s.refreshSeconds()).withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.signalradar.addon.energy", s.energyCost()).withStyle(ChatFormatting.DARK_GREEN));
        if (!s.enabled()) {
            tooltip.add(Component.translatable("tooltip.signalradar.addon.disabled").withStyle(ChatFormatting.RED));
        }
    }
}
