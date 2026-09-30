// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * One installed addon of a radar: its item id and how many items sit in its slot (1, or up to the item's max stack size
 * for a {@link AddonDefinition#stackable() stackable} addon such as the battery).
 *
 * <p>Persistent form: a plain id string when the count is 1 (exactly the old {@code List<ResourceLocation>} format, so
 * radars saved before counts existed load unchanged as count 1), {@code {"id": .., "count": n}} otherwise.
 * Decoding maps the old {@code signalradar:addon_loot} id to {@code signalradar:addon_lootr}.
 */
public record AddonEntry(ResourceLocation id, int count) {
    /** Upper bound of a stored count (any addon item stacks to at most 99). */
    public static final int MAX_COUNT = 99;

    public AddonEntry {
        count = Math.max(1, Math.min(MAX_COUNT, count));
    }

    public static AddonEntry of(ResourceLocation id) {
        return new AddonEntry(id, 1);
    }

    /** Maps ids of renamed addons to their current id (applied when decoding saved radars). */
    public static ResourceLocation migrate(ResourceLocation id) {
        return AddonRegistry.LEGACY_LOOT.equals(id) ? AddonRegistry.LOOTR : id;
    }

    private static final Codec<AddonEntry> FULL = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.xmap(AddonEntry::migrate, x -> x).fieldOf("id").forGetter(AddonEntry::id),
            Codec.INT.optionalFieldOf("count", 1).forGetter(AddonEntry::count)).apply(i, AddonEntry::new));

    public static final Codec<AddonEntry> CODEC = Codec.either(ResourceLocation.CODEC, FULL).xmap(
            e -> e.map(id -> of(migrate(id)), x -> x),
            x -> x.count() == 1 ? Either.left(x.id()) : Either.right(x));

    public static final Codec<List<AddonEntry>> LIST_CODEC = CODEC.listOf();

    public static final StreamCodec<ByteBuf, AddonEntry> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, AddonEntry::id, ByteBufCodecs.VAR_INT, AddonEntry::count, AddonEntry::new);
}
