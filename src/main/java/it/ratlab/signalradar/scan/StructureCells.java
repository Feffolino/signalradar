// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Coarse region grid of the structure cache (pure, no game classes). A search result belongs to the cell of the
 * position it was searched from; a player entering a new cell gets new searches, while the results of the
 * surrounding cells (the previous one included) keep showing until replaced.
 */
public final class StructureCells {
    /** Separator between the locator part of a cache key and the cell part. Never appears in a resource location. */
    public static final char CELL_SEPARATOR = '@';

    private StructureCells() {}

    /** Cell index of a block coordinate for cells of {@code cellSize} blocks (floor division, so negatives work). */
    public static int cell(int blockCoord, int cellSize) {
        return Math.floorDiv(blockCoord, Math.max(1, cellSize));
    }

    /** Cache key of one cell: {@code base@cx,cz}. */
    public static String key(String base, int cx, int cz) {
        return base + CELL_SEPARATOR + cx + "," + cz;
    }

    /** True for a key made by {@link #key}; older saves used keys without a cell. */
    public static boolean isCellKey(String key) {
        return key.indexOf(CELL_SEPARATOR) >= 0;
    }

    /** The locator part ({@code base}) of a cell key, or the key itself when it has no cell. */
    public static String base(String key) {
        int i = key.lastIndexOf(CELL_SEPARATOR);
        return i < 0 ? key : key.substring(0, i);
    }

    /** Keys of the 3x3 cells around {@code (cx, cz)}, the centre one first. */
    public static List<String> neighbourhood(String base, int cx, int cz) {
        List<String> out = new ArrayList<>(9);
        out.add(key(base, cx, cz));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    out.add(key(base, cx + dx, cz + dz));
                }
            }
        }
        return out;
    }
}
