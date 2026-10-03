// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.data;

import it.ratlab.signalradar.scan.StructureCells;
import it.ratlab.signalradar.target.Locator;
import java.util.Collections;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Structure search results, stored in the overworld data folder as {@code data/signalradar_structures.dat}.
 * Key = dimension + locator key (structure id/tag + search radius) + region cell of the search origin
 * ({@link StructureCells}). A hit keeps its position (structures do not move); a miss keeps the game time of the
 * failed search so it can be retried later. At most {@link #MAX_ENTRIES} entries are kept: the least recently used
 * one is evicted first, and the order survives saving.
 */
public final class StructureCacheData extends SavedData {
    private static final String NAME = "signalradar_structures";
    /** Size bound of the cache (hits and misses together). */
    public static final int MAX_ENTRIES = 4096;

    /** {@code pos == null} is a miss; {@code time} is the game time of the search. */
    public record Entry(@Nullable BlockPos pos, long time) {}

    private final LruMap<String, Entry> entries;

    public StructureCacheData() {
        this(MAX_ENTRIES);
    }

    /** Tests: a smaller bound. */
    public StructureCacheData(int maxEntries) {
        this.entries = new LruMap<>(maxEntries);
    }

    public static StructureCacheData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                StructureCacheData::load, StructureCacheData::new, NAME);
    }

    /** Locator part of the key: dimension + locator key (without a cell). */
    public static String key(ResourceKey<Level> dim, Locator.Structure locator) {
        return dim.location() + "|" + locator.key();
    }

    /** Full key of the cell containing {@code origin}. */
    public static String key(ResourceKey<Level> dim, Locator.Structure locator, BlockPos origin, int cellSize) {
        return StructureCells.key(key(dim, locator), StructureCells.cell(origin.getX(), cellSize), StructureCells.cell(origin.getZ(), cellSize));
    }

    /** The entry (and marks it as recently used). */
    @Nullable
    public Entry entry(String key) {
        return entries.get(key);
    }

    /**
     * Nearest (horizontally, to {@code origin}) hit among the 3x3 cells around {@code (cx, cz)} for the locator
     * part {@code base}; null when none of them has a hit.
     */
    @Nullable
    public BlockPos nearestHit(String base, int cx, int cz, BlockPos origin) {
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (String k : StructureCells.neighbourhood(base, cx, cz)) {
            Entry e = entries.get(k);
            if (e == null || e.pos() == null) {
                continue;
            }
            long dx = e.pos().getX() - origin.getX();
            long dz = e.pos().getZ() - origin.getZ();
            long d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = e.pos();
            }
        }
        return best;
    }

    /** The most recently used hit of any cell for the locator part {@code base} (callers without a position). */
    @Nullable
    public BlockPos latestHit(String base) {
        String prefix = base + StructureCells.CELL_SEPARATOR;
        BlockPos last = null;
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            if (e.getValue().pos() != null && e.getKey().startsWith(prefix)) {
                last = e.getValue().pos();
            }
        }
        return last;
    }

    public void putFound(String key, BlockPos pos, long time) {
        entries.put(key, new Entry(pos.immutable(), time));
        setDirty();
    }

    public void putMiss(String key, long time) {
        entries.put(key, new Entry(null, time));
        setDirty();
    }

    public void clear() {
        entries.clear();
        setDirty();
    }

    public int maxEntries() {
        return entries.maxEntries();
    }

    /** Read-only view, least recently used first. Iterating it does not change the order. */
    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entries.forEach((k, e) -> {
            CompoundTag c = new CompoundTag();
            c.putString("key", k);
            c.putLong("time", e.time());
            if (e.pos() != null) {
                c.putLong("pos", e.pos().asLong());
            }
            list.add(c);
        });
        tag.put("entries", list);
        return tag;
    }

    /** Entries without a cell (saves before the region cache) are dropped: they were found from an unknown position. */
    public static StructureCacheData load(CompoundTag tag) {
        StructureCacheData d = new StructureCacheData();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            String key = c.getString("key");
            if (!StructureCells.isCellKey(key)) {
                continue;
            }
            BlockPos pos = c.contains("pos") ? BlockPos.of(c.getLong("pos")) : null;
            d.entries.put(key, new Entry(pos, c.getLong("time")));
        }
        return d;
    }
}
