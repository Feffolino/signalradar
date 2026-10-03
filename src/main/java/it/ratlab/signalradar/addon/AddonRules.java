// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import it.ratlab.signalradar.item.RadarItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Who may install what, and which installed addons count for a scan. Shared by the menu, the command and the scanner. */
public final class AddonRules {
    private AddonRules() {}

    /** Reason an addon cannot be used on a radar of {@code tier}, empty when fine. */
    public static Optional<Component> refusal(AddonSettings s, int tier) {
        if (!AddonRegistry.isActive(s.def())) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.unavailable"));
        }
        if (!s.enabled()) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.disabled"));
        }
        if (s.minTier() > tier) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.min_tier", s.minTier()));
        }
        return Optional.empty();
    }

    /** Installed ids that still exist (unknown ids of removed mods are dropped), in slot order. */
    public static List<ResourceLocation> installed(ItemStack radar) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : RadarItem.addons(radar)) {
            if (AddonRegistry.get(id).filter(AddonRegistry::isActive).isPresent() && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** How many items of addon {@code id} one slot holds: {@link AddonRegistry#BATTERY_STACK} for stackable addons, else 1. */
    public static int maxCount(ResourceLocation id) {
        return AddonRegistry.get(id).map(d -> d.stackable() ? AddonRegistry.BATTERY_STACK : 1).orElse(1);
    }

    /** Count stored for an installed addon id (0 when not installed). */
    public static int count(ItemStack radar, ResourceLocation id) {
        for (AddonEntry e : RadarItem.addonEntries(radar)) {
            if (e.id().equals(id)) {
                return e.count();
            }
        }
        return 0;
    }

    /**
     * Batteries that add capacity: the battery entry's count when it is installed within the tier's slots and usable
     * (enabled, tier high enough); 0 otherwise. Capped at the battery stack size.
     */
    public static int batteries(ItemStack radar) {
        List<AddonEntry> entries = RadarItem.addonEntries(radar);
        if (entries.isEmpty()) {
            return 0;
        }
        int tier = RadarItem.tier(radar);
        int slots = RadarItem.slots(tier);
        int seen = 0;
        List<ResourceLocation> ids = new ArrayList<>();
        for (AddonEntry e : entries) {
            Optional<AddonDefinition> def = AddonRegistry.get(e.id()).filter(AddonRegistry::isActive);
            if (def.isEmpty() || ids.contains(e.id())) {
                continue; // dropped like in installed()
            }
            ids.add(e.id());
            if (seen++ >= slots) {
                break;
            }
            if (e.id().equals(AddonRegistry.BATTERY)) {
                return AddonSettings.of(def.get()).usableAt(tier) ? Math.min(e.count(), AddonRegistry.BATTERY_STACK) : 0;
            }
        }
        return 0;
    }

    /** Installed addons that are enabled and allowed at the radar's tier, limited to the slots the tier has. */
    public static List<AddonSettings> active(ItemStack radar) {
        int tier = RadarItem.tier(radar);
        int slots = RadarItem.slots(tier);
        List<AddonSettings> out = new ArrayList<>();
        for (ResourceLocation id : installed(radar)) {
            if (out.size() >= slots) {
                break;
            }
            AddonSettings s = AddonSettings.of(AddonRegistry.get(id).orElseThrow());
            if (s.usableAt(tier)) {
                out.add(s);
            }
        }
        return out;
    }

    public static int energyCost(List<AddonSettings> addons) {
        long sum = 0;
        for (AddonSettings a : addons) {
            sum += a.energyCost();
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Refusal for installing {@code id} through the command, empty when it fits. */
    public static Optional<Component> installRefusal(ItemStack radar, ResourceLocation id) {
        Optional<AddonDefinition> def = AddonRegistry.get(id);
        if (def.isEmpty()) {
            return Optional.of(Component.translatable("command.signalradar.addon.unknown", id.toString()));
        }
        int tier = RadarItem.tier(radar);
        Optional<Component> r = refusal(AddonSettings.of(def.get()), tier);
        if (r.isPresent()) {
            return r;
        }
        List<ResourceLocation> now = installed(radar);
        if (now.contains(id)) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.duplicate"));
        }
        if (now.size() >= RadarItem.slots(tier)) {
            return Optional.of(Component.translatable("command.signalradar.addon.full"));
        }
        return Optional.empty();
    }
}
