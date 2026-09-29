// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import com.mojang.datafixers.util.Pair;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.target.Locator;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * Server queue for structure searches. {@link #get} never searches: it returns the cached hit, or queues the search
 * (a missing entry, or a miss older than {@link #MISS_RETRY_TICKS}) and returns empty ("pending, don't show yet").
 * {@link #tick} runs at most {@code max} searches per call.
 */
public final class StructureLookupService {
    /** A cached miss is retried at most every 5 minutes. */
    public static final long MISS_RETRY_TICKS = 5L * 60 * 20;

    /** Searches for a structure; returns null when none was found. */
    public interface Finder {
        @Nullable
        BlockPos find(ServerLevel level, Locator.Structure locator, BlockPos origin);
    }

    private record Request(String key, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin) {}

    public static final StructureLookupService INSTANCE = new StructureLookupService(StructureLookupService::vanillaFind);

    private final Finder finder;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();

    public StructureLookupService(Finder finder) {
        this.finder = finder;
    }

    /** Cached hit, or empty (and the search is queued when needed). */
    public Optional<BlockPos> get(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin, long now) {
        String key = StructureCacheData.key(dim, locator);
        StructureCacheData.Entry e = data.entry(key);
        if (e != null && e.pos() != null) {
            return Optional.of(e.pos());
        }
        boolean due = e == null || now - e.time() >= MISS_RETRY_TICKS;
        if (due && queued.add(key)) {
            queue.add(new Request(key, dim, locator, origin.immutable()));
        }
        return Optional.empty();
    }

    /** The cached hit only: never queues a search. */
    public Optional<BlockPos> peek(StructureCacheData data, ResourceKey<Level> dim, Locator.Structure locator) {
        StructureCacheData.Entry e = data.entry(StructureCacheData.key(dim, locator));
        return e == null ? Optional.empty() : Optional.ofNullable(e.pos());
    }

    /** Runs up to {@code max} queued searches; returns how many ran. */
    public int tick(Function<ResourceKey<Level>, ServerLevel> levels, StructureCacheData data, long now, int max) {
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
            try {
                found = finder.find(level, r.locator(), r.origin());
            } catch (RuntimeException e) {
                SignalRadar.LOGGER.warn("Structure search {} failed: {}", r.key(), e.toString());
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
        return queue.size();
    }

    public void clearQueue() {
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
