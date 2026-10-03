// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import com.mojang.datafixers.util.Pair;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.target.Locator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * Server queue for structure searches, cached per region cell ({@link StructureCells}). {@link #get} never searches:
 * it queues the search of the origin's cell when needed (no entry yet, or a miss older than {@link #MISS_RETRY_TICKS})
 * and returns the nearest hit already cached for that cell or one of its 8 neighbours (empty = "pending / nothing,
 * don't show"). So a player entering a new cell keeps the previous cell's results until the new search replaces
 * them. {@link #tick} runs at most {@code max} searches per call.
 */
public final class StructureLookupService {
    /** A cached miss is retried at most every 5 minutes (per cell). */
    public static final long MISS_RETRY_TICKS = 5L * 60 * 20;
    /** A single search slower than this logs one warning per structure. */
    public static final long SLOW_LOOKUP_MS = 200;

    /** Searches for a structure; returns null when none was found. */
    public interface Finder {
        @Nullable
        BlockPos find(ServerLevel level, Locator.Structure locator, BlockPos origin);
    }

    private record Request(String key, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin) {}

    public static final StructureLookupService INSTANCE = new StructureLookupService(StructureLookupService::vanillaFind, true);

    private final Finder finder;
    private final boolean async;
    private final ExecutorService executor;
    private Request inProgress;
    private Future<BlockPos> inProgressFuture;
    private long inProgressStart;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> slowWarned = new HashSet<>();

    public StructureLookupService(Finder finder) {
        this(finder, false);
    }

    public StructureLookupService(Finder finder, boolean async) {
        this.finder = finder;
        this.async = async;
        this.executor = async ? Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "SignalRadar-Structure-Worker");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    /** {@link #get(StructureCacheData, ResourceKey, Locator.Structure, BlockPos, long, int)} with {@code scan.structureCellSize}. */
    public Optional<BlockPos> get(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin, long now) {
        return get(data, dim, locator, origin, now, SignalRadarConfig.structureCellSize());
    }

    /** Nearest cached hit around the origin's cell, or empty; queues the search of the origin's cell when due. */
    public Optional<BlockPos> get(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin, long now,
                                  int cellSize) {
        String base = StructureCacheData.key(dim, locator);
        int cx = StructureCells.cell(origin.getX(), cellSize);
        int cz = StructureCells.cell(origin.getZ(), cellSize);
        String key = StructureCells.key(base, cx, cz);
        StructureCacheData.Entry e = data.entry(key);
        boolean due = e == null || (e.pos() == null && now - e.time() >= MISS_RETRY_TICKS);
        if (due && queued.add(key)) {
            queue.add(new Request(key, dim, locator, origin.immutable()));
        }
        return Optional.ofNullable(data.nearestHit(base, cx, cz, origin));
    }

    /** The cached hit nearest to {@code origin} (its cell and the 8 around it) only: never queues a search. */
    public Optional<BlockPos> peek(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin) {
        int cs = SignalRadarConfig.structureCellSize();
        return Optional.ofNullable(data.nearestHit(StructureCacheData.key(dim, locator), StructureCells.cell(origin.getX(), cs),
                StructureCells.cell(origin.getZ(), cs), origin));
    }

    /** Without a position: the most recently used hit of any cell. Never queues a search. */
    public Optional<BlockPos> peek(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator) {
        return Optional.ofNullable(data.latestHit(StructureCacheData.key(dim, locator)));
    }

    /** Runs queued searches; in async mode offloads findNearestMapStructure to background worker so server thread never blocks. */
    public int tick(Function<ResourceKey<Level>, ServerLevel> levels, StructureCacheData data, long now, int max) {
        if (async) {
            if (inProgressFuture != null) {
                if (!inProgressFuture.isDone()) {
                    return 0;
                }
                BlockPos found = null;
                try {
                    found = inProgressFuture.get();
                } catch (CancellationException e) {
                    inProgress = null;
                    inProgressFuture = null;
                    return 0;
                } catch (Exception e) {
                    SignalRadar.LOGGER.warn("Structure search {} failed: {}", inProgress.key(), e.toString());
                }
                long ms = (System.nanoTime() - inProgressStart) / 1_000_000L;
                SignalRadar.LOGGER.debug("Structure search {} took {} ms ({})", inProgress.key(), ms,
                        found == null ? "miss" : "hit " + found.toShortString());
                if (ms > SLOW_LOOKUP_MS && slowWarned.size() < 1024 && slowWarned.add(inProgress.locator().id().toString())) {
                    SignalRadar.LOGGER.warn("Structure search for {} took {} ms (radius {} chunks); lower scan.structureSearchMaxChunks or "
                            + "set the signalradar:scannable_structures tag if this repeats", inProgress.locator().id(), ms, inProgress.locator().searchRadiusChunks());
                }
                if (found != null) {
                    data.putFound(inProgress.key(), found, now);
                } else {
                    data.putMiss(inProgress.key(), now);
                }
                inProgress = null;
                inProgressFuture = null;
            }

            while (!queue.isEmpty()) {
                Request r = queue.poll();
                queued.remove(r.key());
                ServerLevel level = levels.apply(r.dim());
                if (level == null) {
                    continue;
                }
                inProgress = r;
                inProgressStart = System.nanoTime();
                inProgressFuture = executor.submit(() -> finder.find(level, r.locator(), r.origin()));
                return 1;
            }
            return 0;
        }

        int done = 0;
        while (done < max && !queue.isEmpty()) {
            Request r = queue.poll();
            queued.remove(r.key());
            ServerLevel level = levels.apply(r.dim());
            if (level == null) {
                continue;
            }
            done++;
            BlockPos found = null;
            long start = System.nanoTime();
            try {
                found = finder.find(level, r.locator(), r.origin());
            } catch (RuntimeException e) {
                SignalRadar.LOGGER.warn("Structure search {} failed: {}", r.key(), e.toString());
            }
            long ms = (System.nanoTime() - start) / 1_000_000L;
            SignalRadar.LOGGER.debug("Structure search {} took {} ms ({})", r.key(), ms, found == null ? "miss" : "hit " + found.toShortString());
            if (ms > SLOW_LOOKUP_MS && slowWarned.size() < 1024 && slowWarned.add(r.locator().id().toString())) {
                SignalRadar.LOGGER.warn("Structure search for {} took {} ms (radius {} chunks); lower scan.structureSearchMaxChunks or "
                        + "set the signalradar:scannable_structures tag if this repeats", r.locator().id(), ms, r.locator().searchRadiusChunks());
            }
            if (found != null) {
                data.putFound(r.key(), found, now);
            } else {
                data.putMiss(r.key(), now);
            }
        }
        return done;
    }

    public int pending() {
        return queue.size() + (inProgress != null ? 1 : 0);
    }

    public void clearQueue() {
        if (inProgressFuture != null) {
            inProgressFuture.cancel(true);
            inProgressFuture = null;
            inProgress = null;
        }
        queue.clear();
        queued.clear();
    }

    @Nullable
    private static BlockPos vanillaFind(ServerLevel level, Locator.Structure loc, BlockPos origin) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        HolderSet<Structure> set;
        if (loc.tag()) {
            Optional<HolderSet.Named<Structure>> tag = registry.getTag(TagKey.create(Registries.STRUCTURE, loc.id()));
            if (tag.isEmpty()) {
                return null;
            }
            set = tag.get();
        } else {
            Optional<Holder.Reference<Structure>> h = registry.getHolder(ResourceKey.create(Registries.STRUCTURE, loc.id()));
            if (h.isEmpty()) {
                return null;
            }
            set = HolderSet.direct(List.of(h.get()));
        }
        Pair<BlockPos, Holder<Structure>> p = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, set, origin, loc.searchRadiusChunks(), false);
        return p == null ? null : p.getFirst();
    }
}
