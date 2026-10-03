// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.icon.IconSpec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Client side resolution of blip icon specs ({@link IconSpec}) into something the display can draw. Resolved once per spec
 * string and cached, so the renderer does no registry or resource lookups per frame. The cache is cleared on resource reload
 * and on logout. Main (render) thread only.
 */
final class RadarIcons {
    /** What to draw: nothing special (a dot), textured layers, or an item model. */
    enum Kind { DOT, SPRITE, ITEM, HEAD, BODY }

    /** A textured rectangle: the sprite region {@code u0..u1 x v0..v1} of the texture of {@code type}. */
    record Layer(RenderType type, float u0, float v0, float u1, float v1) {}

    /**
     * @param body     full-body render (kind BODY)
     * @param fallback drawn instead of a BODY whose render failed (vanilla head item, or null = empty frame)
     */
    record Icon(Kind kind, Layer[] layers, @Nullable ItemStack stack, @Nullable MobFaces.Head head, @Nullable MobBodies.Body body,
                @Nullable Icon fallback) {
        static final Icon DOT = new Icon(Kind.DOT, new Layer[0], null, null, null, null);

        Icon(Kind kind, Layer[] layers, @Nullable ItemStack stack) {
            this(kind, layers, stack, null, null, null);
        }

        /** Groups icons sharing a texture so texture switches (and so batch flushes) are few. */
        int sortKey() {
            return kind == Kind.SPRITE ? System.identityHashCode(layers[0].type())
                    : kind == Kind.HEAD && head != null ? System.identityHashCode(head.type()) : kind.ordinal();
        }
    }

    private static final Map<String, Icon> CACHE = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel faceLevel;
    private static final RandomSource RANDOM = RandomSource.create(42L);

    private RadarIcons() {}

    /** The icon of a spec string (never null; {@link Icon#DOT} when it cannot be drawn). */
    static Icon get(String spec) {
        // Faces hold model parts of the current renderers: drop them with the level they were made in.
        var level = Minecraft.getInstance().level;
        if (level != faceLevel) {
            if (level != null && faceLevel != null) {
                CACHE.clear();
            }
            faceLevel = level;
        }
        Icon c = CACHE.get(spec);
        if (c != null) {
            return c;
        }
        IconSpec parsed = IconSpec.parse(spec);
        Icon icon = resolve(parsed);
        // Skins of players not (yet) in the tab list are not cached, so the real face shows up once they are.
        // Mob faces need a level (dummy entity), so they are retried until there is one.
        boolean retry = parsed.kind() == IconSpec.Kind.PLAYER && icon.kind() == Kind.SPRITE && !knownPlayer(parsed.value())
                || parsed.kind() == IconSpec.Kind.ENTITY && Minecraft.getInstance().level == null;
        if (!retry) {
            CACHE.put(spec, icon);
        }
        return icon;
    }

    static void clear() {
        CACHE.clear();
        faceLevel = null;
    }

    private static Icon resolve(IconSpec s) {
        try {
            return switch (s.kind()) {
                case NONE -> Icon.DOT;
                case ITEM -> item(new ResourceLocation(s.value()));
                case BLOCK -> block(new ResourceLocation(s.value()));
                case TEXTURE -> texture(new ResourceLocation(s.value()));
                case ENTITY -> entity(s.value());
                case PLAYER -> player(UUID.fromString(s.value()));
            };
        } catch (RuntimeException e) {
            return Icon.DOT;
        }
    }

    private static Icon itemIcon(@Nullable Item item) {
        return item == null || item == Items.AIR ? Icon.DOT : new Icon(Kind.ITEM, new Layer[0], new ItemStack(item));
    }

    private static Icon item(ResourceLocation id) {
        return itemIcon(BuiltInRegistries.ITEM.getOptional(id).orElse(null));
    }

    private static Icon block(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return Icon.DOT;
        }
        TextureAtlasSprite sprite = blockFace(block);
        if (sprite == null) {
            return itemIcon(block.asItem());
        }
        return new Icon(Kind.SPRITE, new Layer[] {new Layer(RenderType.text(TextureAtlas.LOCATION_BLOCKS), sprite.getU0(), sprite.getV0(),
                sprite.getU1(), sprite.getV1())}, null);
    }

    /** The NORTH face of the block's model, else its first quad, else the particle sprite. */
    @Nullable
    private static TextureAtlasSprite blockFace(Block block) {
        BlockState state = block.defaultBlockState();
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        for (Direction side : new Direction[] {Direction.NORTH, null}) {
            RANDOM.setSeed(42L);
            List<BakedQuad> quads = model.getQuads(state, side, RANDOM, ModelData.EMPTY, null);
            if (!quads.isEmpty()) {
                return quads.get(0).getSprite();
            }
        }
        return model.getParticleIcon(ModelData.EMPTY);
    }

    private static Icon texture(ResourceLocation tex) {
        var rm = Minecraft.getInstance().getResourceManager();
        if (rm.getResource(tex).isEmpty()) {
            String fallback = IconSpec.textureFallbackPath(tex.getPath());
            ResourceLocation alt = fallback == null ? null : new ResourceLocation(tex.getNamespace(), fallback);
            if (alt == null || rm.getResource(alt).isEmpty()) {
                return Icon.DOT;
            }
            tex = alt;
        }
        return new Icon(Kind.SPRITE, new Layer[] {new Layer(RenderType.text(tex), 0f, 0f, 1f, 1f)}, null);
    }

    /** Entity types whose icon path was already logged (once per type per game session). */
    private static final Set<String> LOGGED = new HashSet<>();

    /**
     * The mob's own face (head model part), else a full-body mini render of the mob, else the vanilla mob head item, else
     * a dot. Never the spawn egg (eggs do not tell mobs apart). The chosen path is logged once per type at INFO
     * ({@code Mob icon <type>: face|body|head|dot}) so pack makers can report odd icons.
     */
    private static Icon entity(String typeId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(typeId)).orElse(null);
        String headId = IconSpec.headItemFor(typeId);
        Icon headItem = headId == null ? null : item(new ResourceLocation(headId));
        if (headItem != null && headItem.kind() == Kind.DOT) {
            headItem = null;
        }
        if (type == null) {
            log(typeId, headItem != null ? "head" : "dot", "unknown entity type");
            return headItem != null ? headItem : Icon.DOT;
        }
        MobFaces.Head face = MobFaces.create(type);
        if (face != null) {
            log(typeId, "face", null);
            return new Icon(Kind.HEAD, new Layer[0], null, face, null, null);
        }
        String why = "face: " + MobFaces.lastFailure;
        MobBodies.Body body = MobBodies.create(type);
        if (body != null) {
            log(typeId, "body", why);
            return new Icon(Kind.BODY, new Layer[0], null, null, body, headItem);
        }
        why += "; body: " + MobBodies.lastFailure;
        if (headItem != null) {
            log(typeId, "head", why);
            return headItem;
        }
        log(typeId, "dot", why);
        return Icon.DOT;
    }

    static void log(String typeId, String path, @Nullable String why) {
        if (LOGGED.add(typeId + "|" + path)) {
            if (why == null) {
                SignalRadar.LOGGER.info("Mob icon {}: {}", typeId, path);
            } else {
                SignalRadar.LOGGER.info("Mob icon {}: {} ({})", typeId, path, why);
            }
        }
    }

    private static boolean knownPlayer(String uuid) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfo(UUID.fromString(uuid)) != null;
    }

    /** Face (8,8) plus hat layer (40,8) of the 64x64 skin. */
    private static Icon player(UUID uuid) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        ResourceLocation skin = info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(uuid);
        RenderType type = RenderType.text(skin);
        float px = 1f / 64f;
        return new Icon(Kind.SPRITE, new Layer[] {new Layer(type, 8 * px, 8 * px, 16 * px, 16 * px),
                new Layer(type, 40 * px, 8 * px, 48 * px, 16 * px)}, null);
    }
}
