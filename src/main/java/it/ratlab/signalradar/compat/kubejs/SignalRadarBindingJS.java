// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.api.SignalRadarAPI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code SignalRadar} binding of server scripts, a thin wrapper of {@link SignalRadarAPI}. Ids are strings
 * ({@code 'ns:path'}; without a namespace {@code kubejs:} is assumed). Player methods do nothing / return false for a
 * client-side player.
 */
public final class SignalRadarBindingJS {
    /**
     * Lenient id parsing for script calls: null / blank / malformed ids give null (and a warning in the log) instead of an
     * exception. Same namespace rule as {@link SignalRadarKubeEvents#rl}: no namespace means {@code kubejs:}, a leading
     * {@code #} is ignored.
     */
    @Nullable
    public static ResourceLocation idOrNull(@Nullable String s) {
        if (s == null || s.isBlank()) {
            SignalRadar.LOGGER.warn("SignalRadar binding: empty id ignored");
            return null;
        }
        String t = s.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        ResourceLocation rl = ResourceLocation.tryParse(t.contains(":") ? t : "kubejs:" + t);
        if (rl == null) {
            SignalRadar.LOGGER.warn("SignalRadar binding: invalid id '{}' ignored", s);
        }
        return rl;
    }

    private static boolean radar(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && SignalRadarAPI.isRadar(stack);
    }

    @Nullable
    private static ServerPlayer sp(Player player) {
        return player instanceof ServerPlayer s ? s : null;
    }

    // ------------------------------------------------------------------ radar stack

    public boolean isRadar(ItemStack stack) {
        return radar(stack);
    }

    public int getTier(ItemStack radar) {
        return radar(radar) ? SignalRadarAPI.getTier(radar) : 0;
    }

    public void setTier(ItemStack radar, int tier) {
        if (radar(radar)) {
            SignalRadarAPI.setTier(radar, tier);
        }
    }

    public int getEnergy(ItemStack radar) {
        return radar(radar) ? SignalRadarAPI.getEnergy(radar) : 0;
    }

    public void setEnergy(ItemStack radar, int fe) {
        if (radar(radar)) {
            SignalRadarAPI.setEnergy(radar, fe);
        }
    }

    /** Installed addon ids in slot order. */
    public List<String> getAddons(ItemStack radar) {
        List<String> out = new ArrayList<>();
        if (radar(radar)) {
            SignalRadarAPI.getAddons(radar).forEach(r -> out.add(r.toString()));
        }
        return out;
    }

    public boolean hasAddon(ItemStack radar, String addonId) {
        ResourceLocation id = idOrNull(addonId);
        return id != null && radar(radar) && SignalRadarAPI.hasAddon(radar, id);
    }

    // ------------------------------------------------------------------ player progress

    public void unlock(Player player, String target) {
        ServerPlayer p = sp(player);
        ResourceLocation id = idOrNull(target);
        if (p != null && id != null) {
            SignalRadarAPI.unlock(p, id);
        }
    }

    public void lock(Player player, String target) {
        ServerPlayer p = sp(player);
        ResourceLocation id = idOrNull(target);
        if (p != null && id != null) {
            SignalRadarAPI.lock(p, id);
        }
    }

    public boolean isUnlocked(Player player, String target) {
        ServerPlayer p = sp(player);
        ResourceLocation id = idOrNull(target);
        return p != null && id != null && SignalRadarAPI.isUnlocked(p, id);
    }

    public boolean isFound(Player player, String target) {
        ServerPlayer p = sp(player);
        ResourceLocation id = idOrNull(target);
        return p != null && id != null && SignalRadarAPI.isFound(p, id);
    }

    /** Clears the found flag of one target ({@code 'all'} = every target) and its stage. */
    public void resetFound(Player player, String target) {
        ServerPlayer p = sp(player);
        if (p == null) {
            return;
        }
        if ("all".equals(target)) {
            SignalRadarAPI.resetAllFound(p);
        } else {
            ResourceLocation id = idOrNull(target);
            if (id != null) {
                SignalRadarAPI.resetFound(p, id);
            }
        }
    }

    // ------------------------------------------------------------------ targets

    /** Known position of a target in this level ({@code pos}, cached {@code structure}, loaded {@code entity}), or null. */
    @Nullable
    public Vec3 getTargetPos(Level level, String target) {
        ResourceLocation id = idOrNull(target);
        return id != null && level instanceof ServerLevel sl ? SignalRadarAPI.getTargetPos(sl, id) : null;
    }

    /** Every loaded narrative target id, sorted. */
    public List<String> targets() {
        List<String> out = new ArrayList<>();
        SignalRadarAPI.targetIds().forEach(r -> out.add(r.toString()));
        out.sort(null);
        return out;
    }
}
