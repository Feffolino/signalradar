// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.ratlab.signalradar.registry.ModAttachments;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player radar state, stored as a data attachment (persisted, copied on death). Immutable: change it with the
 * {@code with*} methods and {@link #set}.
 *
 * @param unlocked  targets unlocked for this player (used by targets with {@code requires_unlock})
 * @param found     targets this player has found
 * @param lastDeath where the player last died, until they get close to it again
 */
public record PlayerData(Set<ResourceLocation> unlocked, Set<ResourceLocation> found, Optional<DeathPoint> lastDeath) {
    public static final PlayerData EMPTY = new PlayerData(Set.of(), Set.of(), Optional.empty());

    /** A death position. */
    public record DeathPoint(ResourceLocation dimension, BlockPos pos) {
        public static final Codec<DeathPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(DeathPoint::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(DeathPoint::pos)).apply(i, DeathPoint::new));
    }

    private static final Codec<Set<ResourceLocation>> ID_SET = ResourceLocation.CODEC.listOf()
            .xmap(PlayerData::sorted, l -> List.copyOf(l));

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
        return player.getData(ModAttachments.PLAYER_DATA);
    }

    public void set(Player player) {
        player.setData(ModAttachments.PLAYER_DATA, this);
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
