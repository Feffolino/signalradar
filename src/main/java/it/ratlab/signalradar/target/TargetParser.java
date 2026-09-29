// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.target;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.item.RadarItem;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/** JSON to {@link TargetDef}. Never throws from {@link #parse}: a bad file logs a warning and yields empty. */
public final class TargetParser {
    private static final String OVERWORLD = "minecraft:overworld";

    private TargetParser() {}

    public static Optional<TargetDef> parse(ResourceLocation id, JsonElement json) {
        try {
            return Optional.of(parseOrThrow(id, json));
        } catch (RuntimeException e) {
            SignalRadar.LOGGER.warn("Skipping signalradar target {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    public static TargetDef parseOrThrow(ResourceLocation id, JsonElement json) {
        JsonObject o = GsonHelper.convertToJsonObject(json, "target");
        Component name = o.has("name")
                ? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o.get("name")).getOrThrow(m -> new IllegalArgumentException("bad name: " + m))
                : Component.literal(id.getPath());
        String category = GsonHelper.getAsString(o, "category", "narrative");
        int minTier = GsonHelper.getAsInt(o, "min_tier", 0);
        if (minTier < 0 || minTier > RadarItem.MAX_TIER) {
            throw new IllegalArgumentException("min_tier must be 0.." + RadarItem.MAX_TIER + ", got " + minTier);
        }
        int color = parseColor(o.get("color"));
        int reveal = Math.max(0, GsonHelper.getAsInt(o, "reveal_distance", TargetDef.DEFAULT_REVEAL_DISTANCE));
        String stage = o.has("requires_stage") && !o.get("requires_stage").isJsonNull() ? GsonHelper.getAsString(o, "requires_stage") : null;
        boolean requiresUnlock = GsonHelper.getAsBoolean(o, "requires_unlock", false);
        int foundRadius = Math.max(0, GsonHelper.getAsInt(o, "found_radius", TargetDef.DEFAULT_FOUND_RADIUS));
        boolean hideWhenFound = GsonHelper.getAsBoolean(o, "hide_when_found", false);
        Locator locator = parseLocator(GsonHelper.getAsJsonObject(o, "locator"));
        return new TargetDef(id, name, category, minTier, color, reveal, stage, requiresUnlock, foundRadius, hideWhenFound, locator);
    }

    static int parseColor(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return TargetDef.DEFAULT_COLOR;
        }
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsInt() & 0xFFFFFF;
        }
        String s = GsonHelper.convertToString(e, "color").trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            throw new IllegalArgumentException("color must be #RRGGBB, got '" + s + "'");
        }
        return Integer.parseInt(s, 16);
    }

    static Locator parseLocator(JsonObject l) {
        String type = GsonHelper.getAsString(l, "type");
        switch (type) {
            case "pos": {
                JsonElement p = l.get("pos");
                if (p == null) {
                    throw new IllegalArgumentException("pos locator needs 'pos'");
                }
                BlockPos pos;
                if (p.isJsonArray()) {
                    JsonArray a = p.getAsJsonArray();
                    if (a.size() != 3) {
                        throw new IllegalArgumentException("'pos' array needs 3 numbers");
                    }
                    pos = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
                } else {
                    JsonObject po = GsonHelper.convertToJsonObject(p, "pos");
                    pos = new BlockPos(GsonHelper.getAsInt(po, "x"), GsonHelper.getAsInt(po, "y", 64), GsonHelper.getAsInt(po, "z"));
                }
                return new Locator.Pos(pos, resource(GsonHelper.getAsString(l, "dimension", OVERWORLD)));
            }
            case "structure": {
                String s = GsonHelper.getAsString(l, "structure");
                boolean tag = s.startsWith("#");
                int radius = Mth.clamp(GsonHelper.getAsInt(l, "search_radius_chunks", 100), 1, 1000);
                return new Locator.Structure(resource(tag ? s.substring(1) : s), tag, radius);
            }
            case "entity": {
                String et = l.has("entity_type") ? GsonHelper.getAsString(l, "entity_type") : null;
                String tag = l.has("tag") ? GsonHelper.getAsString(l, "tag") : null;
                if (et == null && tag == null) {
                    throw new IllegalArgumentException("entity locator needs 'entity_type' and/or 'tag'");
                }
                return new Locator.Entity(et == null ? null : resource(et), tag);
            }
            case "block": {
                String s = GsonHelper.getAsString(l, "block");
                boolean tag = s.startsWith("#");
                int radius = Mth.clamp(GsonHelper.getAsInt(l, "radius", 32), 1, Locator.Block.MAX_RADIUS);
                return new Locator.Block(resource(tag ? s.substring(1) : s), tag, radius);
            }
            default:
                throw new IllegalArgumentException("unknown locator type '" + type + "'");
        }
    }

    private static ResourceLocation resource(String s) {
        ResourceLocation r = ResourceLocation.tryParse(s);
        if (r == null) {
            throw new IllegalArgumentException("invalid resource location '" + s + "'");
        }
        return r;
    }
}
