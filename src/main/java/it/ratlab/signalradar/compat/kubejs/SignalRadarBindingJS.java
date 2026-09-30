// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

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
    private static ResourceLocation id(String id) {
        return SignalRadarKubeEvents.rl(id);
    }

    @Nullable
    private static ServerPlayer sp(Player player) {
        return player instanceof ServerPlayer s ? s : null;
    }

    // ------------------------------------------------------------------ radar stack

    public boolean isRadar(ItemStack stack) {
        return SignalRadarAPI.isRadar(stack);
    }

    public int getTier(ItemStack radar) {
        return SignalRadarAPI.getTier(radar);
    }

    public void setTier(ItemStack radar, int tier) {
        SignalRadarAPI.setTier(radar, tier);
    }

    public int getEnergy(ItemStack radar) {
        return SignalRadarAPI.getEnergy(radar);
    }

    public void setEnergy(ItemStack radar, int fe) {
        SignalRadarAPI.setEnergy(radar, fe);
    }

    /** Installed addon ids in slot order. */
    public List<String> getAddons(ItemStack radar) {
        List<String> out = new ArrayList<>();
        SignalRadarAPI.getAddons(radar).forEach(r -> out.add(r.toString()));
        return out;
    }

    public boolean hasAddon(ItemStack radar, String addonId) {
        return SignalRadarAPI.hasAddon(radar, id(addonId));
    }

    // ------------------------------------------------------------------ player progress

    public void unlock(Player player, String target) {
        ServerPlayer p = sp(player);
        if (p != null) {
            SignalRadarAPI.unlock(p, id(target));
        }
    }

    public void lock(Player player, String target) {
        ServerPlayer p = sp(player);
        if (p != null) {
            SignalRadarAPI.lock(p, id(target));
        }
    }

    public boolean isUnlocked(Player player, String target) {
        ServerPlayer p = sp(player);
        return p != null && SignalRadarAPI.isUnlocked(p, id(target));
    }

    public boolean isFound(Player player, String target) {
        ServerPlayer p = sp(player);
        return p != null && SignalRadarAPI.isFound(p, id(target));
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
            SignalRadarAPI.resetFound(p, id(target));
        }
    }

    // ------------------------------------------------------------------ targets

    /** Known position of a target in this level ({@code pos}, cached {@code structure}, loaded {@code entity}), or null. */
    @Nullable
    public Vec3 getTargetPos(Level level, String target) {
        return level instanceof ServerLevel sl ? SignalRadarAPI.getTargetPos(sl, id(target)) : null;
    }

    /** Every loaded narrative target id, sorted. */
    public List<String> targets() {
        List<String> out = new ArrayList<>();
        SignalRadarAPI.targetIds().forEach(r -> out.add(r.toString()));
        out.sort(null);
        return out;
    }
}
