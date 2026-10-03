// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.target.Locator;
import it.ratlab.signalradar.target.TargetDef;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Resolves a target's position for a player in the player's current dimension. */
public final class Locators {
    private Locators() {}

    /**
     * @param range radar range in blocks for the player's tier (entity search box)
     * @return the real (unfuzzed) position, or empty when unavailable (other dimension, pending structure, nothing found)
     */
    public static Optional<Vec3> locate(ServerPlayer player, TargetDef def, int range, ServerLevel level, long now,
                                        BlockLocatorScan.Budget budget) {
        return locate(player, def.locator(), range, level, now, budget);
    }

    /** Unlimited block-check budget (tests, single lookups). */
    public static Optional<Vec3> locate(ServerPlayer player, Locator locator, int range, ServerLevel level, long now) {
        return locate(player, locator, range, level, now, BlockLocatorScan.Budget.unlimited());
    }

    /** @param budget block checks left for this player scan (block locators only) */
    public static Optional<Vec3> locate(ServerPlayer player, Locator locator, int range, ServerLevel level, long now,
                                        BlockLocatorScan.Budget budget) {
        if (locator instanceof Locator.Pos l) {
            if (!level.dimension().location().equals(l.dimension())) {
                return Optional.empty();
            }
            return Optional.of(Vec3.atCenterOf(l.pos()));
        } else if (locator instanceof Locator.Structure l) {
            StructureCacheData data = StructureCacheData.get(level.getServer());
            return StructureLookupService.INSTANCE.get(data, level.dimension(), l, player.blockPosition(), now).map(Vec3::atCenterOf);
        } else if (locator instanceof Locator.Entity l) {
            return nearestEntity(player, l, range, level);
        } else if (locator instanceof Locator.Block l) {
            var match = BlockLocatorScan.matcher(l);
            if (match.isEmpty()) {
                return Optional.empty();
            }
            long ttl = it.ratlab.signalradar.SignalRadarConfig.scanRefreshSeconds() * 20L;
            Optional<BlockPos> found = BlockLocatorScan.INSTANCE.cached(player.getUUID(), l.key() + "@" + level.dimension().location(), now, ttl,
                    () -> BlockLocatorScan.find(level, player.position(), l.radius(), match.get(), budget));
            return found.map(Vec3::atCenterOf);
        }
        return Optional.empty();
    }

    /**
     * Where the target is right now as far as caches know, without starting any lookup: {@code pos} directly, a
     * {@code structure} from the saved cache around the player's cell (never queued), a {@code block} from the player's last block scan, an
     * {@code entity} within {@code entityRange} blocks of the player. Empty when unknown or in another dimension.
     */
    public static Optional<Vec3> peek(ServerPlayer player, TargetDef def, int entityRange, ServerLevel level) {
        Locator locator = def.locator();
        if (locator instanceof Locator.Pos l) {
            return level.dimension().location().equals(l.dimension()) ? Optional.of(Vec3.atCenterOf(l.pos())) : Optional.empty();
        } else if (locator instanceof Locator.Structure l) {
            StructureCacheData data = StructureCacheData.get(level.getServer());
            return StructureLookupService.INSTANCE.peek(data, level.dimension(), l, player.blockPosition()).map(Vec3::atCenterOf);
        } else if (locator instanceof Locator.Entity l) {
            return nearestEntity(player, l, entityRange, level);
        } else if (locator instanceof Locator.Block l) {
            return BlockLocatorScan.INSTANCE.peek(player.getUUID(), l.key() + "@" + level.dimension().location()).map(Vec3::atCenterOf);
        }
        return Optional.empty();
    }

    private static Optional<Vec3> nearestEntity(ServerPlayer player, Locator.Entity l, int range, ServerLevel level) {
        Vec3 origin = player.position();
        return level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(range), e -> matches(e, l, player)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(origin)))
                .map(Entity::position);
    }

    private static boolean matches(Entity e, Locator.Entity l, ServerPlayer self) {
        if (e == self || !e.isAlive()) {
            return false;
        }
        if (l.entityType() != null && !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(l.entityType())) {
            return false;
        }
        return l.tag() == null || e.getTags().contains(l.tag());
    }
}
