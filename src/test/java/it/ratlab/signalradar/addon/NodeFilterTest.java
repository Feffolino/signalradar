// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ratlab.signalradar.addon.detect.NodeFilter;
import it.ratlab.signalradar.addon.detect.NodeFilter.Node;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NodeFilterTest {
    private static Node<String> node(String key, String dim, double x, double z, boolean home) {
        return new Node<>(key, key, dim, x, 60, z, home);
    }

    private static final List<Node<String>> NODES = List.of(
            node("far", "minecraft:overworld", 500, 0, false),
            node("near", "minecraft:overworld", 10, 10, false),
            node("mid", "minecraft:overworld", 100, 0, false),
            node("home", "minecraft:overworld", 5, 5, true),
            node("nether", "minecraft:the_nether", 1, 1, false),
            node("opened", "minecraft:overworld", 20, 0, false));

    @Test
    void dropsHomeOtherDimensionOpenedAndOutOfRange() {
        List<Node<String>> out = NodeFilter.select(NODES, "minecraft:overworld", 0, 0, 160, Set.of("opened")::contains, 100);
        assertEquals(List.of("near", "mid"), out.stream().map(Node::key).toList());
    }

    @Test
    void radiusIsHorizontalAndInclusive() {
        List<Node<String>> out = NodeFilter.select(List.of(node("a", "d", 100, 0, false)), "d", 0, 0, 100, k -> false, 10);
        assertEquals(1, out.size());
        assertTrue(NodeFilter.select(List.of(node("a", "d", 100.1, 0, false)), "d", 0, 0, 100, k -> false, 10).isEmpty());
    }

    @Test
    void sortedNearestFirstAndCapped() {
        List<Node<String>> out = NodeFilter.select(NODES, "minecraft:overworld", 0, 0, 1000, k -> false, 2);
        assertEquals(List.of("near", "opened"), out.stream().map(Node::key).toList());
    }

    @Test
    void emptyNetworkGivesNothing() {
        assertTrue(NodeFilter.select(List.<Node<String>>of(), "d", 0, 0, 50, k -> false, 5).isEmpty());
    }
}
