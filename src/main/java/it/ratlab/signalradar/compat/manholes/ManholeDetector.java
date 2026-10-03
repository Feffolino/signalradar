// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.manholes;

import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.addon.detect.NodeFilter;
import it.ratlab.signalradar.icon.IconSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Manhole Travel nodes the player's network has not opened yet. Reads the node registry (no block scan, so unloaded chunks
 * work); home manholes are excluded. Only class-loaded after a {@code ModList} check.
 */
public final class ManholeDetector {
    private ManholeDetector() {}

    public static List<Hit> run(ServerPlayer player, ServerLevel level, int radius) {
        String dim = level.dimension().location().toString();
        List<NodeFilter.Node<NodeRecord>> all = new ArrayList<>();
        for (NodeRecord r : ManholeData.get(level.getServer()).nodes()) {
            all.add(new NodeFilter.Node<>(r, r.id.toString(), r.dimension.location().toString(), r.pos.getX() + 0.5, r.pos.getY() + 0.5,
                    r.pos.getZ() + 0.5, r.home));
        }
        List<NodeFilter.Node<NodeRecord>> nodes = NodeFilter.select(all, dim, player.getX(), player.getZ(), radius,
                null, true, Detectors.MAX_HITS);
        List<Hit> hits = new ArrayList<>(nodes.size());
        for (NodeFilter.Node<NodeRecord> n : nodes) {
            hits.add(new Hit(n.key(), n.ref().displayName(), n.x(), n.y(), n.z(), 0,
                    IconSpec.manholeTexture(n.ref().lookOrDefault())));
        }
        return hits;
    }
}
