// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test.kubejs;

import dev.latvian.mods.kubejs.core.PlayerKJS;
import net.minecraft.server.level.ServerPlayer;

/** Test bodies that touch KubeJS classes; only reached after a {@code ModList.isLoaded("kubejs")} check. */
public final class KubeJSChecks {
    private KubeJSChecks() {}

    /** Reads the stage straight from KubeJS (not through StageHelper). */
    public static boolean hasStage(ServerPlayer player, String stage) {
        return ((PlayerKJS) player).kjs$getStages().has(stage);
    }
}
