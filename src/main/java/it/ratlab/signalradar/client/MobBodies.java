// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Full-body mini renders for mob blip icons, used when a mob has no drawable head part (GeckoLib mobs, custom models
 * without a head). A dummy entity is created once per type from the client level and kept in the icon cache (which is
 * cleared on level change and logout); it is drawn with its renderer's own {@code render(...)}, front facing (yaw 0),
 * animations frozen (tick 0, no limb swing, partial tick 0), fullbright, scaled to fit the icon square and flattened on z.
 * Works for any {@link EntityRenderer}, including GeckoLib's {@code GeoEntityRenderer}. Render thread only.
 */
final class MobBodies {
    /** A dummy entity ready to draw. {@code failed} is set after a render threw; the caller then uses its fallback. */
    static final class Body {
        final Entity entity;
        final EntityRenderer<Entity> renderer;
        final float width;
        final float height;
        boolean failed;

        Body(Entity entity, EntityRenderer<Entity> renderer, float width, float height) {
            this.entity = entity;
            this.renderer = renderer;
            this.width = width;
            this.height = height;
        }
    }

    /** Why the last {@link #create} returned null (for the one-time log line). */
    static String lastFailure = "";

    private MobBodies() {}

    @Nullable
    @SuppressWarnings("unchecked")
    static Body create(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level == null) {
                lastFailure = "no level";
                return null;
            }
            Entity entity = type.create(mc.level);
            if (entity == null) {
                lastFailure = "type.create returned null";
                return null;
            }
            EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            if (renderer == null) {
                lastFailure = "no renderer";
                return null;
            }
            float w = entity.getBbWidth();
            float h = entity.getBbHeight();
            if (!(w > 0.01f) || !(h > 0.01f) || !Float.isFinite(w + h)) {
                lastFailure = "empty bounding box";
                return null;
            }
            freeze(entity);
            return new Body(entity, (EntityRenderer<Entity>) (EntityRenderer<?>) renderer, w, h);
        } catch (RuntimeException | LinkageError e) {
            lastFailure = e.toString();
            return null;
        }
    }

    /** Front facing (yaw 0 faces +z, the viewer), no animation state. */
    private static void freeze(Entity e) {
        e.tickCount = 0;
        e.setPos(0, 0, 0);
        e.setYRot(0f);
        e.yRotO = 0f;
        e.setXRot(0f);
        e.xRotO = 0f;
        if (e instanceof LivingEntity l) {
            l.yBodyRot = 0f;
            l.yBodyRotO = 0f;
            l.yHeadRot = 0f;
            l.yHeadRotO = 0f;
        }
    }

    /**
     * Draws the entity centred at the current pose origin, fitting a square of {@code side} (model units),
     * {@code thickness} deep. Throws what the renderer throws (the caller marks the body failed).
     */
    static void draw(Body b, PoseStack ps, MultiBufferSource buffers, float side, float thickness, int light) {
        float k = side / Math.max(b.height, b.width) * 0.92f;
        // the caller pushed the pose (and pops it even when the renderer throws)
        ps.translate(0f, -b.height * k / 2f, 0f);
        ps.scale(k, k, thickness / Math.max(b.width, 0.1f));
        freeze(b.entity); // renderers or mods may have touched it
        b.renderer.render(b.entity, 0f, 0f, ps, buffers, light);
    }
}
