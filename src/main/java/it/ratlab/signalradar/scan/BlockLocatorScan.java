// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.target.Locator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

    /** Block states a search may still test during one player scan (shared by all block targets of that scan). */
    public static final class Budget {
        private int remaining;
        private boolean exhausted;

        public Budget(int checks) {
            this.remaining = Math.max(0, checks);
        }

        public static Budget unlimited() {
            return new Budget(Integer.MAX_VALUE);
        }

        /** Takes {@code n} checks; false (nothing taken) when not enough are left. */
        public boolean take(int n) {
            if (remaining < n) {
                remaining = 0;
                exhausted = true;
                return false;
            }
            remaining -= n;
            return true;
        }

        /** A {@link #take} was refused: searches sharing this budget may have stopped early. */
        public boolean exhausted() {
            return exhausted;
        }

        public int remaining() {
            return remaining;
        }
    }

    public static Optional<BlockPos> find(ServerLevel level, Vec3 center, int radius, Predicate<BlockState> match) {
        return find(level, center, radius, match, Budget.unlimited());
    }

    /**
     * Nearest matching block within {@code radius} (sphere) of {@code center}, loaded chunks only. Sections that
     * cannot contain a match ({@link LevelChunkSection#maybeHas}) or lie outside the radius are skipped. Every section
     * that is actually searched costs 4096 from {@code budget}; when it runs out the nearest block found so far wins.
     */
    public static Optional<BlockPos> find(ServerLevel level, Vec3 center, int radius, Predicate<BlockState> match, Budget budget) {
        double[] bestD = {Double.MAX_VALUE};
        BlockPos[] best = {null};
        visit(level, center, radius, match, budget, (p, d) -> {
            if (d < bestD[0]) {
                bestD[0] = d;
                best[0] = p.immutable();
            }
            return true;
        });
        return Optional.ofNullable(best[0]);
    }

    /**
     * Every matching block within {@code radius} (sphere) of {@code center}, nearest first, at most {@code limit} raw
     * matches are examined (the search stops there), same section skipping and budget rules as {@link #find}.
     */
    public static List<BlockPos> findAll(ServerLevel level, Vec3 center, int radius, Predicate<BlockState> match, Budget budget, int limit) {
        List<BlockPos> out = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        visit(level, center, radius, match, budget, (p, d) -> {
            out.add(p.immutable());
            dist.add(d);
            return out.size() < limit;
        });
        Integer[] order = new Integer[out.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingDouble(dist::get));
        List<BlockPos> sorted = new ArrayList<>(order.length);
        for (int i : order) {
            sorted.add(out.get(i));
        }
        return sorted;
    }

    /** Receives each matching block (mutable position, squared distance to the centre); returns false to stop. */
    private interface Visitor {
        boolean hit(BlockPos.MutableBlockPos pos, double dist2);
    }

    private static void visit(ServerLevel level, Vec3 center, int radius, Predicate<BlockState> match, Budget budget, Visitor visitor) {
        int minCx = (int) Math.floor((center.x - radius) / 16.0);
        int maxCx = (int) Math.floor((center.x + radius) / 16.0);
        int minCz = (int) Math.floor((center.z - radius) / 16.0);
        int maxCz = (int) Math.floor((center.z + radius) / 16.0);
        double r2 = (double) radius * radius;
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
                    if (!budget.take(4096)) {
                        return;
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
                                if (d <= r2 && !visitor.hit(p, d)) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
