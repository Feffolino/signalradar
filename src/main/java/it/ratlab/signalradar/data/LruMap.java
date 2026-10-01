// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access-ordered map holding at most {@code maxEntries}; adding one more evicts the least recently used entry.
 * Iteration runs from the least to the most recently used. Not thread safe (server thread only).
 */
public final class LruMap<K, V> extends LinkedHashMap<K, V> {
    private final int maxEntries;

    public LruMap(int maxEntries) {
        super(16, 0.75f, true);
        this.maxEntries = Math.max(1, maxEntries);
    }

    public int maxEntries() {
        return maxEntries;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxEntries;
    }
}
