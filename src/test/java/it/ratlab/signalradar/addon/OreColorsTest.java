// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OreColorsTest {
    @Test
    void tableHasTheSpecifiedColours() {
        assertEquals(0xD8AF93, OreColors.builtIn("iron"));
        assertEquals(0x4AEDD9, OreColors.builtIn("diamond"));
        assertEquals(0x8A5A4A, OreColors.builtIn("ancient_debris"));
        assertEquals(OreColors.builtIn("aluminum"), OreColors.builtIn("bauxite"));
        assertEquals(-1, OreColors.builtIn("unobtainium"));
    }

    @Test
    void unknownMaterialsGetAStableHashColour() {
        int a = OreColors.hashColor("unobtainium");
        assertEquals(a, OreColors.hashColor("unobtainium"));
        assertNotEquals(a, OreColors.hashColor("vibranium"));
        assertEquals(a, OreColors.colorOf("unobtainium", Map.of()));
        assertTrue(a >= 0 && a <= 0xFFFFFF);
        // brightness 0.95 saturation 0.6: the biggest channel is 242 and the smallest 97 (=0.95 * 0.4 * 255 rounded)
        int max = Math.max(a >> 16 & 255, Math.max(a >> 8 & 255, a & 255));
        int min = Math.min(a >> 16 & 255, Math.min(a >> 8 & 255, a & 255));
        assertEquals(242, max);
        assertEquals(97, min);
    }

    @Test
    void overridesWinOverTableAndAreParsedLeniently() {
        Map<String, Integer> o = OreColors.parseOverrides(List.of("Iron=#112233", "bad", "x=#12", "osmium=FF0000", "=#000000"));
        assertEquals(Map.of("iron", 0x112233, "osmium", 0xFF0000), o);
        assertEquals(0x112233, OreColors.colorOf("iron", o));
        assertEquals(0xFF0000, OreColors.colorOf("osmium", o));
        assertEquals(0x4AEDD9, OreColors.colorOf("diamond", o));
    }

    @Test
    void materialComesFromTheOresSubTagPath() {
        assertEquals("iron", OreColors.materialOfTagPath("ores/iron"));
        assertEquals("iron", OreColors.materialOfTagPath("ores/iron/nether"));
        assertNull(OreColors.materialOfTagPath("ores"));
        assertNull(OreColors.materialOfTagPath("ores_in_ground/stone"));
        assertNull(OreColors.materialOfTagPath("storage_blocks/iron"));
    }
}
