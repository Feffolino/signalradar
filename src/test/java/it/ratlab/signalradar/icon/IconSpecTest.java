// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IconSpecTest {
    @Test
    void parsesEveryKind() {
        assertEquals(new IconSpec(IconSpec.Kind.BLOCK, "minecraft:iron_ore"), IconSpec.parse("block:minecraft:iron_ore"));
        assertEquals(new IconSpec(IconSpec.Kind.ITEM, "minecraft:compass"), IconSpec.parse("item:minecraft:compass"));
        assertEquals(new IconSpec(IconSpec.Kind.ENTITY, "minecraft:zombie"), IconSpec.parse("entity:minecraft:zombie"));
        assertEquals(new IconSpec(IconSpec.Kind.TEXTURE, "manholes:textures/gui/map_icon_city.png"),
                IconSpec.parse("texture:manholes:textures/gui/map_icon_city.png"));
        assertEquals(IconSpec.Kind.PLAYER, IconSpec.parse("player:123e4567-e89b-12d3-a456-426614174000").kind());
    }

    @Test
    void defaultNamespaceAndRoundTrip() {
        assertEquals("item:minecraft:map", IconSpec.parse("item:map").toString());
        assertEquals("block:minecraft:stone", IconSpec.parse("BLOCK:Stone").toString());
    }

    @Test
    void blankAndGarbageAreDots() {
        for (String s : new String[] {null, "", "  ", "nothing", "weird:minecraft:x", "item:Not Valid!", "player:not-a-uuid", "item:"}) {
            assertTrue(IconSpec.parse(s).isNone(), "'" + s + "' should be a dot");
        }
        assertEquals("", IconSpec.NONE.toString());
    }

    @Test
    void normalizeTreatsBareIdsAsItems() {
        assertEquals("item:minecraft:map", IconSpec.normalize("minecraft:map", IconSpec.DEFAULT_TARGET));
        assertEquals("item:minecraft:map", IconSpec.normalize("map", IconSpec.DEFAULT_TARGET));
        assertEquals("block:minecraft:gold_ore", IconSpec.normalize("block:minecraft:gold_ore", IconSpec.DEFAULT_TARGET));
        assertEquals(IconSpec.DEFAULT_TARGET, IconSpec.normalize(null, IconSpec.DEFAULT_TARGET));
        assertEquals(IconSpec.DEFAULT_TARGET, IconSpec.normalize("  ", IconSpec.DEFAULT_TARGET));
        assertThrows(IllegalArgumentException.class, () -> IconSpec.normalize("banana:::", "x"));
        assertThrows(IllegalArgumentException.class, () -> IconSpec.normalize("player:zzz", "x"));
        assertTrue(IconSpec.isValid("texture:mod:textures/a.png"));
        assertFalse(IconSpec.isValid("block:UPPER CASE"));
    }

    @Test
    void mobHeadsByEntityType() {
        assertEquals("minecraft:zombie_head", IconSpec.headItemFor("minecraft:zombie"));
        assertEquals("minecraft:skeleton_skull", IconSpec.headItemFor("minecraft:skeleton"));
        assertEquals("minecraft:wither_skeleton_skull", IconSpec.headItemFor("minecraft:wither_skeleton"));
        assertEquals("minecraft:creeper_head", IconSpec.headItemFor("minecraft:creeper"));
        assertEquals("minecraft:piglin_head", IconSpec.headItemFor("minecraft:piglin"));
        assertEquals("minecraft:piglin_head", IconSpec.headItemFor("minecraft:zombified_piglin"));
        assertEquals("minecraft:dragon_head", IconSpec.headItemFor("minecraft:ender_dragon"));
        assertNull(IconSpec.headItemFor("minecraft:pig"));
        assertNull(IconSpec.headItemFor("other:zombie"));
    }

    @Test
    void manholeTexturesByLook() {
        assertEquals("texture:manholes:textures/gui/map_icon_city.png", IconSpec.manholeTexture("city"));
        assertEquals("texture:manholes:textures/gui/map_icon_home_manhole.png", IconSpec.manholeTexture("home_manhole"));
        assertEquals("texture:other:textures/gui/map_icon_x.png", IconSpec.manholeTexture("other:x"));
        assertEquals(IconSpec.MANHOLE_FALLBACK, IconSpec.manholeTexture(""));
        assertEquals(IconSpec.MANHOLE_FALLBACK, IconSpec.manholeTexture(null));
        assertEquals(IconSpec.MANHOLE_FALLBACK, IconSpec.manholeTexture("Bad Look!"));
    }

    @Test
    void textureFallbackOnlyForPerLookMapIcons() {
        assertEquals("textures/gui/map_icon.png", IconSpec.textureFallbackPath("textures/gui/map_icon_city.png"));
        assertNull(IconSpec.textureFallbackPath("textures/gui/map_icon.png"));
        assertNull(IconSpec.textureFallbackPath("textures/gui/other.png"));
    }
}
