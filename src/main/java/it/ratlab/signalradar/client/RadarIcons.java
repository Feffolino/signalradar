// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import it.ratlab.signalradar.icon.IconSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Client side resolution of blip icon specs ({@link IconSpec}) into something the display can draw. Resolved once per spec
 * string and cached, so the renderer does no registry or resource lookups per frame. The cache is cleared on resource reload
 * and on logout. Main (render) thread only.
 */
final class RadarIcons {
    /** What to draw: nothing special (a dot), textured layers, or an item model. */
    enum Kind { DOT, SPRITE, ITEM }

    /** A textured rectangle: the sprite region {@code u0..u1 x v0..v1} of the texture of {@code type}. */
    record Layer(RenderType type, float u0, float v0, float u1, float v1) {}

    record Icon(Kind kind, Layer[] layers, @Nullable ItemStack stack) {
        static final Icon DOT = new Icon(Kind.DOT, new Layer[0], null);

        /** Groups icons sharing a texture so texture switches (and so batch flushes) are few. */
        int sortKey() {
            return kind == Kind.SPRITE ? System.identityHashCode(layers[0].type()) : kind.ordinal();
        }
    }

    private static final Map<String, Icon> CACHE = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    private RadarIcons() {}

    /** The icon of a spec string (never null; {@link Icon#DOT} when it cannot be drawn). */
    static Icon get(String spec) {
        Icon c = CACHE.get(spec);
        if (c != null) {
            return c;
        }
        IconSpec parsed = IconSpec.parse(spec);
        Icon icon = resolve(parsed);
        // Skins of players not (yet) in the tab list are not cached, so the real face shows up once they are.
        if (!(parsed.kind() == IconSpec.Kind.PLAYER && icon.kind() == Kind.SPRITE && !knownPlayer(parsed.value()))) {
            CACHE.put(spec, icon);
        }
        return icon;
    }

    static void clear() {
        CACHE.clear();
    }

    private static Icon resolve(IconSpec s) {
        try {
            return switch (s.kind()) {
                case NONE -> Icon.DOT;
                case ITEM -> item(ResourceLocation.parse(s.value()));
                case BLOCK -> block(ResourceLocation.parse(s.value()));
                case TEXTURE -> texture(ResourceLocation.parse(s.value()));
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
            ResourceLocation alt = fallback == null ? null : ResourceLocation.fromNamespaceAndPath(tex.getNamespace(), fallback);
            if (alt == null || rm.getResource(alt).isEmpty()) {
                return Icon.DOT;
            }
            tex = alt;
        }
        return new Icon(Kind.SPRITE, new Layer[] {new Layer(RenderType.text(tex), 0f, 0f, 1f, 1f)}, null);
    }

    /** Mob head item when vanilla has one, else the spawn egg, else a dot. */
    private static Icon entity(String typeId) {
        String head = IconSpec.headItemFor(typeId);
        if (head != null) {
            return item(ResourceLocation.parse(head));
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(typeId)).orElse(null);
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        return itemIcon(egg);
    }

    private static boolean knownPlayer(String uuid) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfo(UUID.fromString(uuid)) != null;
    }

    /** Face (8,8) plus hat layer (40,8) of the 64x64 skin. */
    private static Icon player(UUID uuid) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(uuid);
        RenderType type = RenderType.text(skin.texture());
        float px = 1f / 64f;
        return new Icon(Kind.SPRITE, new Layer[] {new Layer(type, 8 * px, 8 * px, 16 * px, 16 * px),
                new Layer(type, 40 * px, 8 * px, 48 * px, 16 * px)}, null);
    }
}
