// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.kubejs;

import dev.latvian.mods.kubejs.core.PlayerKJS;
import dev.latvian.mods.kubejs.stages.Stages;
import it.ratlab.signalradar.progress.StageHelper;
import net.minecraft.server.level.ServerPlayer;

/** KubeJS stages backend. Only instantiated when KubeJS is loaded. */
public final class KubeJSStages implements StageHelper.Backend {
    private static Stages stages(ServerPlayer player) {
        return ((PlayerKJS) player).kjs$getStages();
    }

    @Override
    public boolean has(ServerPlayer player, String stage) {
        return stages(player).has(stage);
    }

    @Override
    public void add(ServerPlayer player, String stage) {
        stages(player).add(stage);
    }

    @Override
    public void remove(ServerPlayer player, String stage) {
        stages(player).remove(stage);
    }
}
