// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test.compat;

import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.test.CompatGameTests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Manhole addon checks; only loaded when Manhole Travel is present. */
public final class ManholeChecks {
    private ManholeChecks() {}

    private static boolean has(List<Hit> hits, NodeRecord n) {
        return hits.stream().anyMatch(x -> x.key().equals(n.id.toString()));
    }

    public static void run(GameTestHelper h) {
        ServerPlayer p = CompatGameTests.player(h);
        NodeRecord near = ManholesAPI.place(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), "Alpha", null, false, null);
        NodeRecord far = ManholesAPI.place(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3).offset(120, 0, 0)), "Beta", null, false, null);
        NodeRecord home = ManholesAPI.place(h.getLevel(), h.absolutePos(new BlockPos(5, 1, 5)), "Mine", null, false, null);
        h.assertTrue(near != null && far != null && home != null, "manholes could not be placed");
        try {
            home.home = true;
            List<Hit> hits = CompatGameTests.run(AddonRegistry.MANHOLE, p, h, 160);
            h.assertTrue(has(hits, near) && has(hits, far), "unopened nodes missing: " + hits.size());
            h.assertTrue(!has(hits, home), "home manhole listed");
            Hit alpha = hits.stream().filter(x -> x.key().equals(near.id.toString())).findFirst().orElseThrow();
            h.assertTrue(alpha.name().getString().equals("Alpha"), "blip name " + alpha.name().getString());
            h.assertTrue(alpha.icon().startsWith("texture:manholes:textures/gui/map_icon_") && alpha.icon().endsWith(".png"),
                    "manhole icon " + alpha.icon());
            h.assertTrue(!has(CompatGameTests.run(AddonRegistry.MANHOLE, p, h, 50), far), "radius ignored");
            ManholesAPI.open(p, near);
            List<Hit> after = CompatGameTests.run(AddonRegistry.MANHOLE, p, h, 160);
            h.assertTrue(!has(after, near) && has(after, far), "opened node still listed / other node lost");
        } finally {
            for (NodeRecord n : new NodeRecord[] {near, far, home}) {
                if (n != null) {
                    ManholeData.get(h.getLevel().getServer()).removeNode(n.id);
                    h.getLevel().removeBlock(n.pos, false);
                }
            }
        }
    }
}
