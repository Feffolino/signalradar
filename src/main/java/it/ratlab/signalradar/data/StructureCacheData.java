// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.data;

import it.ratlab.signalradar.target.Locator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
 * Key = dimension + locator key. A hit keeps its position forever (structures do not move); a miss keeps the game
 * time of the failed search so it can be retried later.
 */
public final class StructureCacheData extends SavedData {
    private static final String NAME = "signalradar_structures";

    /** {@code pos == null} is a miss; {@code time} is the game time of the search. */
    public record Entry(@Nullable BlockPos pos, long time) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static StructureCacheData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StructureCacheData::new, StructureCacheData::load), NAME);
    }

    public static String key(ResourceKey<Level> dim, Locator.Structure locator) {
        return dim.location() + "|" + locator.key();
    }

    @Nullable
    public Entry entry(String key) {
        return entries.get(key);
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

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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

    public static StructureCacheData load(CompoundTag tag, HolderLookup.Provider registries) {
        StructureCacheData d = new StructureCacheData();
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = c.contains("pos") ? BlockPos.of(c.getLong("pos")) : null;
            d.entries.put(c.getString("key"), new Entry(pos, c.getLong("time")));
        }
        return d;
    }
}
