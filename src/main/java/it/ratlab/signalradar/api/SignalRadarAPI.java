// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.api;

import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.progress.PlayerData;
import it.ratlab.signalradar.progress.StageHelper;
import it.ratlab.signalradar.scan.Locators;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.Locator;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Public API of Signal Radar (server side; the KubeJS binding wraps it). All methods are static and cheap: none starts
 * a structure search or a block scan.
 */
public final class SignalRadarAPI {
    private SignalRadarAPI() {}

    // ------------------------------------------------------------------ targets

    /** Ids of every loaded narrative target, sorted. */
    public static Set<ResourceLocation> targetIds() {
        Set<ResourceLocation> ids = new TreeSet<>();
        TargetManager.all().forEach(d -> ids.add(d.id()));
        return ids;
    }

    @Nullable
    public static TargetDef getTarget(ResourceLocation id) {
        return TargetManager.get(id);
    }

    /**
     * Known position of a target in this level: {@code pos} targets directly, {@code structure} targets from the cache
     * (null while unresolved), {@code entity} targets = first loaded matching entity. {@code block} targets need a
     * player, see {@link #getTargetPos(ServerPlayer, ResourceLocation)}. Null when unknown.
     */
    @Nullable
    public static Vec3 getTargetPos(ServerLevel level, ResourceLocation id) {
        TargetDef def = TargetManager.get(id);
        if (def == null) {
            return null;
        }
        if (def.locator() instanceof Locator.Pos l) {
            return level.dimension().location().equals(l.dimension()) ? Vec3.atCenterOf(l.pos()) : null;
        } else if (def.locator() instanceof Locator.Structure l) {
            return StructureLookupService.INSTANCE.peek(StructureCacheData.get(level.getServer()), level.dimension(), l)
                    .map(Vec3::atCenterOf).orElse(null);
        } else if (def.locator() instanceof Locator.Entity l) {
            for (Entity e : level.getAllEntities()) {
                if (e.isAlive() && (l.entityType() == null || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(l.entityType()))
                        && (l.tag() == null || e.getTags().contains(l.tag()))) {
                    return e.position();
                }
            }
        }
        return null;
    }

    /** As {@link #getTargetPos(ServerLevel, ResourceLocation)}, plus block targets from the player's last block scan. */
    @Nullable
    public static Vec3 getTargetPos(ServerPlayer player, ResourceLocation id) {
        TargetDef def = TargetManager.get(id);
        if (def == null) {
            return null;
        }
        return Locators.peek(player, def, def.foundRadius(), player.serverLevel()).orElse(null);
    }

    // ------------------------------------------------------------------ per-player progression

    public static void unlock(ServerPlayer player, ResourceLocation target) {
        PlayerData.get(player).withUnlocked(target, true).set(player);
    }

    public static void lock(ServerPlayer player, ResourceLocation target) {
        PlayerData.get(player).withUnlocked(target, false).set(player);
    }

    public static boolean isUnlocked(ServerPlayer player, ResourceLocation target) {
        return PlayerData.get(player).isUnlocked(target);
    }

    public static boolean isFound(ServerPlayer player, ResourceLocation target) {
        return PlayerData.get(player).isFound(target);
    }

    /** Forgets that the target was found and removes its found stage. */
    public static void resetFound(ServerPlayer player, ResourceLocation target) {
        PlayerData.get(player).withFound(target, false).set(player);
        StageHelper.remove(player, StageHelper.foundStage(target));
    }

    /** {@link #resetFound} for every target the player has found. */
    public static void resetAllFound(ServerPlayer player) {
        for (ResourceLocation id : List.copyOf(PlayerData.get(player).found())) {
            resetFound(player, id);
        }
    }

    /** Where the player died last, if the marker has not been cleared yet (may be another dimension). */
    public static Optional<PlayerData.DeathPoint> getLastDeath(ServerPlayer player) {
        return PlayerData.get(player).lastDeath();
    }

    // ------------------------------------------------------------------ radar stacks

    public static boolean isRadar(ItemStack stack) {
        return stack.getItem() instanceof RadarItem;
    }

    public static int getTier(ItemStack radar) {
        return RadarItem.tier(radar);
    }

    public static void setTier(ItemStack radar, int tier) {
        RadarItem.setTier(radar, tier);
    }

    public static int getEnergy(ItemStack radar) {
        return RadarItem.energy(radar);
    }

    public static void setEnergy(ItemStack radar, int fe) {
        RadarItem.setEnergy(radar, fe);
    }

    /** FE capacity of this radar (base capacity plus installed batteries). */
    public static int getCapacity(ItemStack radar) {
        return RadarItem.capacity(radar);
    }

    /** Items installed for addon {@code id} (battery stacks count up to 8), 0 when not installed. */
    public static int getAddonCount(ItemStack radar, ResourceLocation id) {
        return it.ratlab.signalradar.addon.AddonRules.count(radar, id);
    }

    public static List<ResourceLocation> getAddons(ItemStack radar) {
        return RadarItem.addons(radar);
    }

    public static void setAddons(ItemStack radar, List<ResourceLocation> ids) {
        RadarItem.setAddons(radar, ids);
    }

    public static boolean hasAddon(ItemStack radar, ResourceLocation id) {
        return RadarItem.addons(radar).contains(id);
    }
}
