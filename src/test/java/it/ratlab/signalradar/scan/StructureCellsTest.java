// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.ratlab.signalradar.data.LruMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructureCellsTest {
    @Test
    void cellsUseFloorDivisionSoNegativesDoNotShareCellZero() {
        assertEquals(0, StructureCells.cell(0, 256));
        assertEquals(0, StructureCells.cell(255, 256));
        assertEquals(1, StructureCells.cell(256, 256));
        assertEquals(-1, StructureCells.cell(-1, 256));
        assertEquals(-1, StructureCells.cell(-256, 256));
        assertEquals(-2, StructureCells.cell(-257, 256));
        assertEquals(7, StructureCells.cell(7, 0)); // a bad size never divides by zero
    }

    @Test
    void keysCarryTheCellAndCanBeSplitBack() {
        String k = StructureCells.key("minecraft:overworld|structure:minecraft:village_plains:32", -3, 4);
        assertEquals("minecraft:overworld|structure:minecraft:village_plains:32@-3,4", k);
        assertTrue(StructureCells.isCellKey(k));
        assertFalse(StructureCells.isCellKey("minecraft:overworld|structure:minecraft:village_plains:32"));
        assertEquals("minecraft:overworld|structure:minecraft:village_plains:32", StructureCells.base(k));
    }

    @Test
    void neighbourhoodIsTheThreeByThreeBlockCentreFirst() {
        List<String> n = StructureCells.neighbourhood("b", 0, 0);
        assertEquals(9, n.size());
        assertEquals("b@0,0", n.get(0));
        assertTrue(n.contains("b@-1,-1") && n.contains("b@1,1") && n.contains("b@-1,1") && n.contains("b@0,-1"));
        assertEquals(9, n.stream().distinct().count());
    }

    @Test
    void lruEvictsTheLeastRecentlyUsed() {
        LruMap<String, Integer> m = new LruMap<>(2);
        m.put("a", 1);
        m.put("b", 2);
        m.get("a");
        m.put("c", 3);
        assertNull(m.get("b"));
        assertEquals(1, m.get("a"));
        assertEquals(2, m.size());
    }

    @Test
    void lruIteratesOldestFirst() {
        LruMap<String, Integer> m = new LruMap<>(3);
        m.put("a", 1);
        m.put("b", 2);
        m.put("c", 3);
        m.get("a");
        assertEquals(List.of("b", "c", "a"), List.copyOf(m.keySet()));
        m.put("d", 4);
        assertEquals(List.of("c", "a", "d"), List.copyOf(m.keySet()));
    }
}
