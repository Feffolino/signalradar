// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.target;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** How a narrative target finds its position. Only the player's current dimension is ever considered. */
public sealed interface Locator {
    /** Stable text key (used for caches). */
    String key();

    /** Fixed position in a dimension. */
    record Pos(BlockPos pos, ResourceLocation dimension) implements Locator {
        @Override
        public String key() {
            return "pos:" + dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
    }

    /** Nearest structure: id is {@code ns:name}, or a tag ({@code #ns:tag} in JSON; tag = true, id without the hash). */
    record Structure(ResourceLocation id, boolean tag, int searchRadiusChunks) implements Locator {
        @Override
        public String key() {
            return "structure:" + (tag ? "#" : "") + id + ":" + searchRadiusChunks;
        }
    }

    /** Nearest loaded entity, optionally filtered by type and scoreboard tag (at least one is set). */
    record Entity(@Nullable ResourceLocation entityType, @Nullable String tag) implements Locator {
        @Override
        public String key() {
            return "entity:" + (entityType == null ? "*" : entityType) + ":" + (tag == null ? "*" : tag);
        }
    }

    /** Nearest block (id or tag) within {@code radius} in loaded chunks. */
    record Block(ResourceLocation id, boolean tag, int radius) implements Locator {
        @Override
        public String key() {
            return "block:" + (tag ? "#" : "") + id + ":" + radius;
        }
    }
}
