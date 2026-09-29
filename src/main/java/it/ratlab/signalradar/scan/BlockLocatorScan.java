// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.target.Locator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/** Nearest block search in loaded chunks (never loads chunks), plus a small per-player TTL cache. */
public final class BlockLocatorScan {
    private record Cached(long time, Optional<BlockPos> result) {}

    public static final BlockLocatorScan INSTANCE = new BlockLocatorScan();

    private final Map<UUID, Map<String, Cached>> cache = new HashMap<>();

    /** Cached result if younger than {@code ttlTicks}, otherwise computes and stores. */
    public Optional<BlockPos> cached(UUID player, String key, long now, long ttlTicks, Supplier<Optional<BlockPos>> compute) {
        Map<String, Cached> m = cache.computeIfAbsent(player, k -> new HashMap<>());
        Cached c = m.get(key);
        if (c != null && now - c.time() < ttlTicks && now >= c.time()) {
            return c.result();
        }
        Optional<BlockPos> r = compute.get();
        m.put(key, new Cached(now, r));
        return r;
    }

    public void forget(UUID player) {
        cache.remove(player);
    }

    public void clear() {
        cache.clear();
    }

    public static Optional<Predicate<BlockState>> matcher(Locator.Block loc) {
        if (loc.tag()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, loc.id());
            return Optional.of(s -> s.is(tag));
        }
        if (!BuiltInRegistries.BLOCK.containsKey(loc.id())) {
            return Optional.empty();
        }
        Block b = BuiltInRegistries.BLOCK.get(loc.id());
        return Optional.of(s -> s.is(b));
    }

    /**
     * Nearest matching block within {@code radius} (sphere) of {@code center}, loaded chunks only. Sections that
     * cannot contain a match ({@link LevelChunkSection#maybeHas}) or lie outside the radius are skipped.
     */
    public static Optional<BlockPos> find(ServerLevel level, Vec3 center, int radius, Predicate<BlockState> match) {
        int minCx = (int) Math.floor((center.x - radius) / 16.0);
        int maxCx = (int) Math.floor((center.x + radius) / 16.0);
        int minCz = (int) Math.floor((center.z - radius) / 16.0);
        int maxCz = (int) Math.floor((center.z + radius) / 16.0);
        double r2 = (double) radius * radius;
        double bestD = Double.MAX_VALUE;
        BlockPos best = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection s = sections[i];
                    int baseY = (chunk.getMinSection() + i) << 4;
                    if (center.y < baseY - radius || center.y > baseY + 16 + radius) {
                        continue;
                    }
                    if (s.hasOnlyAir() || !s.maybeHas(match)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!match.test(s.getBlockState(x, y, z))) {
                                    continue;
                                }
                                p.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                double dx = p.getX() + 0.5 - center.x;
                                double dy = p.getY() + 0.5 - center.y;
                                double dz = p.getZ() + 0.5 - center.z;
                                double d = dx * dx + dy * dy + dz * dz;
                                if (d <= r2 && d < bestD) {
                                    bestD = d;
                                    best = p.immutable();
                                }
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
