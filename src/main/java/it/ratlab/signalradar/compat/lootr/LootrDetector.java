// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.lootr;

import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.icon.IconSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noobanidus.mods.lootr.common.api.data.ILootrInfoProvider;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;
import noobanidus.mods.lootr.common.api.data.entity.ILootrEntity;

/**
 * Lootr containers (block entities and Lootr carts) in loaded chunks that the player has not opened yet. Uses
 * {@link ILootrInfoProvider#hasOpened(net.minecraft.world.entity.player.Player)}, which respects Lootr's team resolver and
 * does not generate any loot. Only class-loaded after a {@code ModList} check.
 */
public final class LootrDetector {
    private LootrDetector() {}

    private record Found(String key, Component name, double x, double y, double z, String icon) {}

    public static List<Hit> run(ServerPlayer player, ServerLevel level, int radius) {
        Vec3 c = player.position();
        double r2 = (double) radius * radius;
        List<Found> found = new ArrayList<>();
        int minCx = (int) Math.floor((c.x - radius) / 16.0);
        int maxCx = (int) Math.floor((c.x + radius) / 16.0);
        int minCz = (int) Math.floor((c.z - radius) / 16.0);
        int maxCz = (int) Math.floor((c.z + radius) / 16.0);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (!(e.getValue() instanceof ILootrBlockEntity lootr)) {
                        continue;
                    }
                    BlockPos p = e.getKey();
                    if (horizontalSq(p.getX() + 0.5, p.getZ() + 0.5, c) <= r2 && !lootr.hasOpened(player)) {
                        found.add(new Found(p.getX() + "," + p.getY() + "," + p.getZ(), level.getBlockState(p).getBlock().getName(),
                                p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                                Detectors.blockItemIcon(level.getBlockState(p).getBlock())));
                    }
                }
            }
        }
        for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(radius),
                en -> en instanceof ILootrEntity && en.isAlive() && horizontalSq(en.getX(), en.getZ(), c) <= r2)) {
            if (!((ILootrEntity) e).hasOpened(player)) {
                found.add(new Found(e.getUUID().toString(), e.getName(), e.getX(), e.getY(), e.getZ(), IconSpec.CHEST));
            }
        }
        found.sort(Comparator.comparingDouble((Found f) -> horizontalSq(f.x(), f.z(), c)).thenComparing(Found::key));
        List<Hit> hits = new ArrayList<>();
        for (Found f : found) {
            if (hits.size() >= Detectors.MAX_HITS) {
                break;
            }
            hits.add(new Hit(f.key(), f.name(), f.x(), f.y(), f.z(), 0, f.icon()));
        }
        return hits;
    }

    private static double horizontalSq(double x, double z, Vec3 c) {
        double dx = x - c.x;
        double dz = z - c.z;
        return dx * dx + dz * dz;
    }
}
