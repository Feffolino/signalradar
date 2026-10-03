// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player radar state, stored in player persistent NBT. Immutable: change it with the
 * {@code with*} methods and {@link #set}.
 *
 * @param unlocked  targets unlocked for this player (used by targets with {@code requires_unlock})
 * @param found     targets this player has found
 * @param lastDeath where the player last died, until they get close to it again
 */
public record PlayerData(Set<ResourceLocation> unlocked, Set<ResourceLocation> found, Optional<DeathPoint> lastDeath) {
    public static final PlayerData EMPTY = new PlayerData(Set.of(), Set.of(), Optional.empty());
    private static final String DATA_KEY = "signalradar_data";

    /** A death position. */
    public record DeathPoint(ResourceLocation dimension, BlockPos pos) {
        public static final Codec<DeathPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(DeathPoint::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(DeathPoint::pos)).apply(i, DeathPoint::new));
    }

    private static final Codec<Set<ResourceLocation>> ID_SET = ResourceLocation.CODEC.listOf()
            .xmap(PlayerData::sorted, List::copyOf);

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ID_SET.optionalFieldOf("unlocked", Set.of()).forGetter(PlayerData::unlocked),
            ID_SET.optionalFieldOf("found", Set.of()).forGetter(PlayerData::found),
            DeathPoint.CODEC.optionalFieldOf("last_death").forGetter(PlayerData::lastDeath)).apply(i, PlayerData::new));

    public PlayerData {
        unlocked = sorted(unlocked);
        found = sorted(found);
    }

    private static Set<ResourceLocation> sorted(java.util.Collection<ResourceLocation> ids) {
        return ids.isEmpty() ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(ids));
    }

    public static PlayerData get(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return EMPTY;
        }
        return fromNbt(persisted.getCompound(DATA_KEY));
    }

    public void set(Player player) {
        CompoundTag nbt = player.getPersistentData();
        CompoundTag persisted = nbt.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(DATA_KEY, toNbt());
        nbt.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag unlockedList = new ListTag();
        for (ResourceLocation id : unlocked) {
            unlockedList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("unlocked", unlockedList);

        ListTag foundList = new ListTag();
        for (ResourceLocation id : found) {
            foundList.add(StringTag.valueOf(id.toString()));
        }
        tag.put("found", foundList);

        lastDeath.ifPresent(d -> {
            CompoundTag deathTag = new CompoundTag();
            deathTag.putString("dimension", d.dimension().toString());
            deathTag.putLong("pos", d.pos().asLong());
            tag.put("last_death", deathTag);
        });
        return tag;
    }

    public static PlayerData fromNbt(CompoundTag tag) {
        Set<ResourceLocation> unlocked = new TreeSet<>();
        ListTag uList = tag.getList("unlocked", Tag.TAG_STRING);
        for (int i = 0; i < uList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(uList.getString(i));
            if (id != null) unlocked.add(id);
        }

        Set<ResourceLocation> found = new TreeSet<>();
        ListTag fList = tag.getList("found", Tag.TAG_STRING);
        for (int i = 0; i < fList.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(fList.getString(i));
            if (id != null) found.add(id);
        }

        Optional<DeathPoint> lastDeath = Optional.empty();
        if (tag.contains("last_death", Tag.TAG_COMPOUND)) {
            CompoundTag deathTag = tag.getCompound("last_death");
            ResourceLocation dim = ResourceLocation.tryParse(deathTag.getString("dimension"));
            if (dim != null) {
                lastDeath = Optional.of(new DeathPoint(dim, BlockPos.of(deathTag.getLong("pos"))));
            }
        }

        return new PlayerData(unlocked, found, lastDeath);
    }

    public boolean isUnlocked(ResourceLocation id) {
        return unlocked.contains(id);
    }

    public boolean isFound(ResourceLocation id) {
        return found.contains(id);
    }

    public PlayerData withUnlocked(ResourceLocation id, boolean on) {
        return new PlayerData(toggle(unlocked, id, on), found, lastDeath);
    }

    public PlayerData withFound(ResourceLocation id, boolean on) {
        return new PlayerData(unlocked, toggle(found, id, on), lastDeath);
    }

    public PlayerData withoutFound() {
        return new PlayerData(unlocked, Set.of(), lastDeath);
    }

    public PlayerData withLastDeath(Optional<DeathPoint> death) {
        return new PlayerData(unlocked, found, death);
    }

    private static Set<ResourceLocation> toggle(Set<ResourceLocation> set, ResourceLocation id, boolean on) {
        TreeSet<ResourceLocation> copy = new TreeSet<>(set);
        if (on) {
            copy.add(id);
        } else {
            copy.remove(id);
        }
        return copy;
    }
}
