// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.progress;

import it.ratlab.signalradar.SignalRadar;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player stages: KubeJS stages when KubeJS is loaded, otherwise scoreboard tags ({@code player.getTags()}).
 * The KubeJS backend lives in {@code compat.kubejs} and is only class-loaded after a {@code ModList} check.
 */
public final class StageHelper {
    /** Backend of the stage storage. */
    public interface Backend {
        boolean has(ServerPlayer player, String stage);

        void add(ServerPlayer player, String stage);

        void remove(ServerPlayer player, String stage);
    }

    /** Scoreboard tags. */
    public static final Backend TAGS = new Backend() {
        @Override
        public boolean has(ServerPlayer player, String stage) {
            return player.getTags().contains(stage);
        }

        @Override
        public void add(ServerPlayer player, String stage) {
            player.addTag(stage);
        }

        @Override
        public void remove(ServerPlayer player, String stage) {
            player.removeTag(stage);
        }
    };

    private static volatile Backend backend = TAGS;

    private StageHelper() {}

    /** Called once from the mod constructor when KubeJS is loaded. Falls back to tags if the integration cannot load. */
    public static void enableKubeJS() {
        try {
            backend = new it.ratlab.signalradar.compat.kubejs.KubeJSStages();
            SignalRadar.LOGGER.info("KubeJS found: stages use KubeJS stages");
        } catch (Throwable t) {
            SignalRadar.LOGGER.error("KubeJS stages integration failed to load, using scoreboard tags", t);
        }
    }

    public static boolean has(ServerPlayer player, String stage) {
        return backend.has(player, stage);
    }

    public static void add(ServerPlayer player, String stage) {
        backend.add(player, stage);
    }

    public static void remove(ServerPlayer player, String stage) {
        backend.remove(player, stage);
    }

    /** Stage given when a player finds a target: {@code signalradar_found_<target path>} ({@code /} becomes {@code _}). */
    public static String foundStage(net.minecraft.resources.ResourceLocation target) {
        return "signalradar_found_" + target.getPath().replace('/', '_');
    }

    /** Test hook: switch the backend. */
    public static void setBackendForTest(Backend b) {
        backend = b;
    }
}
