// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import it.ratlab.signalradar.progress.PlayerData;
import it.ratlab.signalradar.progress.StageHelper;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a scan needs to know about the player's progression (injected so the scanner stays free of world access).
 *
 * @param unlocked  target unlocked for the player
 * @param found     target found by the player
 * @param stage     player has the stage (KubeJS stage or scoreboard tag)
 * @param lastDeath last death position in the player's current dimension, or null
 */
public record PlayerProgress(Predicate<ResourceLocation> unlocked, Predicate<ResourceLocation> found, Predicate<String> stage,
                             @Nullable Vec3 lastDeath) {
    /** Nothing unlocked, found or staged; no last death. */
    public static final PlayerProgress NONE = new PlayerProgress(id -> false, id -> false, s -> false, null);

    public static PlayerProgress of(ServerPlayer player) {
        PlayerData data = PlayerData.get(player);
        Vec3 death = data.lastDeath()
                .filter(d -> d.dimension().equals(player.level().dimension().location()))
                .map(d -> Vec3.atCenterOf(d.pos()))
                .orElse(null);
        return new PlayerProgress(data::isUnlocked, data::isFound, s -> StageHelper.has(player, s), death);
    }
}
