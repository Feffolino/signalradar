// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ore blip colour per material ({@code c:ores/<material>}). Pure logic (no Minecraft classes): a built-in table, a
 * stable hash colour for unknown materials and the config overrides {@code "material=#RRGGBB"}.
 */
public final class OreColors {
    private static final Map<String, Integer> TABLE = new HashMap<>();

    static {
        put(0xD8AF93, "iron");
        put(0xFCEE4B, "gold");
        put(0xE77C56, "copper");
        put(0x6B6B6B, "coal");
        put(0x4AEDD9, "diamond");
        put(0x17DD62, "emerald");
        put(0x345EC3, "lapis");
        put(0xFF3030, "redstone");
        put(0xEAE5DE, "quartz");
        put(0x8A5A4A, "netherite", "netherite_scrap", "ancient_debris");
        put(0x9BB4C8, "osmium");
        put(0xC8D3D6, "tin");
        put(0x7A7AA0, "lead");
        put(0x7DD34A, "uranium");
        put(0xA8B8A0, "zinc");
        put(0xD0D8E0, "silver");
        put(0xC8C08A, "nickel");
        put(0xD8C0A8, "aluminum", "aluminium", "bauxite");
        put(0xB35BD6, "fluorite");
        put(0xB8E0F0, "platinum");
        put(0x3060E0, "cobalt");
        put(0xE0E040, "sulfur", "sulphur");
        put(0xF0F0F0, "salt");
        put(0xA070E0, "amethyst");
    }

    private OreColors() {}

    private static void put(int rgb, String... names) {
        for (String n : names) {
            TABLE.put(n, rgb);
        }
    }

    /** The built-in table colour of a material, or -1 when it is not in the table. */
    public static int builtIn(String material) {
        Integer c = TABLE.get(material);
        return c == null ? -1 : c;
    }

    /** Stable colour from the material name: hue from the hash, saturation 0.6, brightness 0.95. */
    public static int hashColor(String material) {
        int h = material.hashCode();
        h ^= h >>> 16;
        h *= 0x45D9F3B;
        h ^= h >>> 16;
        return hsb(Math.floorMod(h, 360) / 360f, 0.6f, 0.95f);
    }

    /** Override, else table, else hash colour. */
    public static int colorOf(String material, Map<String, Integer> overrides) {
        Integer o = overrides.get(material);
        if (o != null) {
            return o;
        }
        int t = builtIn(material);
        return t >= 0 ? t : hashColor(material);
    }

    /** Parses {@code "material=#RRGGBB"} strings; malformed entries are skipped. Keys are lower case. */
    public static Map<String, Integer> parseOverrides(List<? extends String> entries) {
        Map<String, Integer> out = new HashMap<>();
        if (entries == null) {
            return out;
        }
        for (String e : entries) {
            int eq = e == null ? -1 : e.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            int c = AddonMath.parseColor(e.substring(eq + 1), -1);
            if (c >= 0) {
                out.put(e.substring(0, eq).trim().toLowerCase(Locale.ROOT), c);
            }
        }
        return out;
    }

    /** First path segment after {@code ores/}, or null when the path is not an ore sub-tag ({@code ores} itself is not). */
    public static String materialOfTagPath(String path) {
        if (path == null || !path.startsWith("ores/") || path.length() <= 5) {
            return null;
        }
        int slash = path.indexOf('/', 5);
        return slash < 0 ? path.substring(5) : path.substring(5, slash);
    }

    static int hsb(float hue, float sat, float bri) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float f = h - (float) Math.floor(h);
        float p = bri * (1f - sat);
        float q = bri * (1f - sat * f);
        float t = bri * (1f - sat * (1f - f));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0 -> { r = bri; g = t; b = p; }
            case 1 -> { r = q; g = bri; b = p; }
            case 2 -> { r = p; g = bri; b = t; }
            case 3 -> { r = p; g = q; b = bri; }
            case 4 -> { r = t; g = p; b = bri; }
            default -> { r = bri; g = p; b = q; }
        }
        return ((int) (r * 255f + 0.5f) << 16) | ((int) (g * 255f + 0.5f) << 8) | (int) (b * 255f + 0.5f);
    }
}
