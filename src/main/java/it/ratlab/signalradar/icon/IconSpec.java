// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.icon;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Icon spec strings carried by blips ({@code kind:value}). Pure string logic with no Minecraft or client classes, so the server
 * builds and validates specs and the client resolves them.
 * <ul>
 *   <li>{@code block:<id>}: the face of a block</li>
 *   <li>{@code item:<id>}: an item icon</li>
 *   <li>{@code texture:<rl>}: a plain PNG (resource location of the file, e.g. {@code manholes:textures/gui/map_icon_city.png})</li>
 *   <li>{@code entity:<type id>}: a mob head, else the spawn egg, else a dot</li>
 *   <li>{@code player:<uuid>}: the skin face of a player</li>
 *   <li>empty: a coloured dot</li>
 * </ul>
 */
public record IconSpec(Kind kind, String value) {
    public enum Kind {
        NONE(""), BLOCK("block"), ITEM("item"), TEXTURE("texture"), ENTITY("entity"), PLAYER("player");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    public static final IconSpec NONE = new IconSpec(Kind.NONE, "");
    /** Default icon of narrative targets. */
    public static final String DEFAULT_TARGET = "item:minecraft:compass";
    public static final String STRUCTURE = "item:minecraft:map";
    public static final String CHEST = "item:minecraft:chest";
    public static final String LAST_DEATH = "item:minecraft:skeleton_skull";
    /** Fallback texture of manholes when a look has no icon of its own. */
    public static final String MANHOLE_FALLBACK = "texture:manholes:textures/gui/map_icon.png";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern UUID_TEXT = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Vanilla mob head items by entity type id. */
    private static final Map<String, String> HEADS = Map.of(
            "minecraft:zombie", "minecraft:zombie_head",
            "minecraft:skeleton", "minecraft:skeleton_skull",
            "minecraft:wither_skeleton", "minecraft:wither_skeleton_skull",
            "minecraft:creeper", "minecraft:creeper_head",
            "minecraft:piglin", "minecraft:piglin_head",
            "minecraft:zombified_piglin", "minecraft:piglin_head",
            "minecraft:ender_dragon", "minecraft:dragon_head");

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    @Override
    public String toString() {
        return kind == Kind.NONE ? "" : kind.prefix + ":" + value;
    }

    /** Lenient parse for the client: blank, unknown kinds and malformed values give {@link #NONE} (a dot). */
    public static IconSpec parse(@Nullable String spec) {
        if (spec == null || spec.isBlank()) {
            return NONE;
        }
        int colon = spec.indexOf(':');
        if (colon <= 0) {
            return NONE;
        }
        String prefix = spec.substring(0, colon).toLowerCase(Locale.ROOT);
        String rest = spec.substring(colon + 1).trim();
        for (Kind k : Kind.values()) {
            if (k != Kind.NONE && k.prefix.equals(prefix)) {
                if (k == Kind.PLAYER) {
                    return UUID_TEXT.matcher(rest).matches() ? new IconSpec(k, rest.toLowerCase(Locale.ROOT)) : NONE;
                }
                String id = normalizeId(rest);
                return id == null ? NONE : new IconSpec(k, id);
            }
        }
        return NONE;
    }

    /**
     * Strict parse for data files: a bare id means {@code item:<id>}; blank or null gives {@code def}.
     *
     * @return the normalised spec string
     * @throws IllegalArgumentException for an unknown kind or malformed value
     */
    public static String normalize(@Nullable String raw, String def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        String s = raw.trim();
        int colon = s.indexOf(':');
        boolean prefixed = false;
        if (colon > 0) {
            String p = s.substring(0, colon).toLowerCase(Locale.ROOT);
            for (Kind k : Kind.values()) {
                if (k != Kind.NONE && k.prefix.equals(p)) {
                    prefixed = true;
                    break;
                }
            }
        }
        String full = prefixed ? s : "item:" + s;
        IconSpec parsed = parse(full);
        if (parsed.isNone()) {
            throw new IllegalArgumentException("invalid icon '" + raw + "' (use block:<id>, item:<id>, texture:<path>, entity:<id>, player:<uuid> or a bare item id)");
        }
        return parsed.toString();
    }

    /** True when {@link #normalize} would accept the text. */
    public static boolean isValid(@Nullable String raw) {
        try {
            normalize(raw, "");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Nullable
    private static String normalizeId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        if (s.indexOf(':') < 0) {
            s = "minecraft:" + s;
        }
        return ID.matcher(s).matches() ? s : null;
    }

    public static String block(String id) {
        return "block:" + id;
    }

    public static String item(String id) {
        return "item:" + id;
    }

    public static String entity(String typeId) {
        return "entity:" + typeId;
    }

    public static String player(String uuid) {
        return "player:" + uuid;
    }

    /** The vanilla mob head item id of an entity type, or null when it has none. */
    @Nullable
    public static String headItemFor(String entityTypeId) {
        return HEADS.get(entityTypeId);
    }

    /**
     * Manhole Travel map icon of a node look: {@code city} is {@code manholes:textures/gui/map_icon_city.png}, {@code ns:x} is
     * {@code ns:textures/gui/map_icon_x.png}; blank or malformed is the generic manhole icon.
     */
    public static String manholeTexture(@Nullable String look) {
        if (look == null || look.isBlank()) {
            return MANHOLE_FALLBACK;
        }
        String id = normalizeId(look.contains(":") ? look : "manholes:" + look);
        if (id == null) {
            return MANHOLE_FALLBACK;
        }
        int c = id.indexOf(':');
        return "texture:" + id.substring(0, c) + ":textures/gui/map_icon_" + id.substring(c + 1) + ".png";
    }

    /**
     * Texture to use when {@code path} (namespace excluded) does not exist: a per-look map icon falls back to the same
     * directory's {@code map_icon.png}; anything else has no fallback (null, the client draws a dot).
     */
    @Nullable
    public static String textureFallbackPath(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        if (file.startsWith("map_icon_") && file.endsWith(".png")) {
            return (slash < 0 ? "" : path.substring(0, slash + 1)) + "map_icon.png";
        }
        return null;
    }
}
