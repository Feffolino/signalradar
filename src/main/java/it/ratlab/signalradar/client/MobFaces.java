// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.joml.Vector3f;

/**
 * Mob faces for blip icons: the head {@link ModelPart} of the entity's own renderer model, drawn from the front with the
 * renderer's texture, flattened on z. A dummy entity is created once per type (only to reach its renderer and texture) and
 * dropped; the {@link Head} keeps the model part, so nothing here holds a level. Render thread only.
 *
 * <p>Drawn with {@code RenderType.text(texture)}, like the other sprite icons: true fullbright, no diffuse lighting (the
 * flattened z scale would skew the normals), alpha cutout, and it ignores the overlay and normal the cubes write.
 */
final class MobFaces {
    /** A head ready to draw: model part, texture type and its bounds (model space, blocks, front view, y down). */
    record Head(ModelPart part, RenderType type, float cx, float cy, float cz, float size, float depth) {}

    private MobFaces() {}

    /** The face of an entity type, or null when it has none or anything went wrong (the caller logs and falls back). */
    @Nullable
    @SuppressWarnings("unchecked")
    static Head create(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level == null) {
                return null;
            }
            Entity entity = type.create(mc.level);
            if (entity == null) {
                return null;
            }
            EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
                // GeckoLib (GeoEntityRenderer) and other custom renderers: no vanilla model to take a head from
                lastFailure = "renderer " + (renderer == null ? "null" : renderer.getClass().getSimpleName()) + " is not a LivingEntityRenderer";
                return null;
            }
            Object model = living.getModel();
            ModelPart head = null;
            if (model instanceof HeadedModel headed) {
                head = headed.getHead();
            } else if (model instanceof HierarchicalModel<?> h) {
                head = findChild(h.root(), "head", 0); // often nested: root > body > head
            }
            if (head == null && model != null) {
                head = headField(model); // plain EntityModel with a "head" field (MutantsZombies and many Blockbench exports)
            }
            if (head == null) {
                lastFailure = "model " + (model == null ? "null" : model.getClass().getSimpleName()) + " has no head part";
                return null;
            }
            ResourceLocation tex = ((EntityRenderer<Entity>) (EntityRenderer<?>) renderer).getTextureLocation(entity);
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            float[] saved = neutral(head);
            try {
                measure(head, new PoseStack(), b, new Vector3f());
            } finally {
                restore(head, saved);
            }
            float w = b[3] - b[0];
            float h = b[4] - b[1];
            if (!(w > 1e-4f) || !(h > 1e-4f) || !Float.isFinite(w + h)) {
                lastFailure = "head part has no visible cubes";
                return null;
            }
            return new Head(head, RenderType.text(tex), (b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2, Math.max(w, h),
                    Math.max(b[5] - b[2], 0.05f));
        } catch (RuntimeException | LinkageError e) {
            lastFailure = e.toString();
            return null;
        }
    }

    /** Why the last {@link #create} returned null (for the one-time log line). */
    static String lastFailure = "";

    /** Depth-first search for a descendant part called {@code name} (children map, access transformer). */
    @Nullable
    private static ModelPart findChild(ModelPart part, String name, int depth) {
        if (depth > 8) {
            return null;
        }
        ModelPart direct = part.children.get(name);
        if (direct != null) {
            return direct;
        }
        for (ModelPart child : part.children.values()) {
            ModelPart found = findChild(child, name, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** A {@code ModelPart} field named "head" (any case) of the model class or a superclass, public or not. */
    @Nullable
    private static ModelPart headField(Object model) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(f.getType()) || !f.getName().equalsIgnoreCase("head")
                        || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object v = f.get(model);
                    if (v instanceof ModelPart p) {
                        return p;
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // inaccessible: try the next one
                }
            }
        }
        return null;
    }

    /**
     * Draws the head centred at the current pose origin, filling a square of {@code side}, {@code thickness} deep.
     * The part's own pose is neutralised for the draw and restored (the model is shared with the world renderer).
     */
    static void draw(Head h, PoseStack ps, VertexConsumer c, float side, float thickness, int argb, int light) {
        float k = side / h.size();
        ps.pushPose();
        // model space is x right, y down, front at -z: rotate half a turn about x to look at it from the front
        ps.scale(k, -k, -thickness / h.depth());
        ps.translate(-h.cx(), -h.cy(), -h.cz());
        float[] saved = neutral(h.part());
        try {
            h.part().render(ps, c, light, OverlayTexture.NO_OVERLAY, argb);
        } finally {
            restore(h.part(), saved);
        }
        ps.popPose();
    }

    /** Sets position, rotation and scale of the part to identity; returns the previous values. */
    private static float[] neutral(ModelPart p) {
        float[] s = {p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale};
        p.setPos(0, 0, 0);
        p.setRotation(0, 0, 0);
        p.xScale = 1;
        p.yScale = 1;
        p.zScale = 1;
        return s;
    }

    private static void restore(ModelPart p, float[] s) {
        p.setPos(s[0], s[1], s[2]);
        p.setRotation(s[3], s[4], s[5]);
        p.xScale = s[6];
        p.yScale = s[7];
        p.zScale = s[8];
    }

    /** Grows {@code b} (minXYZ, maxXYZ) by every cube of the visible part tree, in the space of the pose stack. */
    private static void measure(ModelPart part, PoseStack ps, float[] b, Vector3f tmp) {
        if (!part.visible) {
            return;
        }
        ps.pushPose();
        part.translateAndRotate(ps);
        for (ModelPart.Cube cube : part.cubes) {
            for (int i = 0; i < 8; i++) {
                float x = ((i & 1) == 0 ? cube.minX : cube.maxX) / 16f;
                float y = ((i & 2) == 0 ? cube.minY : cube.maxY) / 16f;
                float z = ((i & 4) == 0 ? cube.minZ : cube.maxZ) / 16f;
                ps.last().pose().transformPosition(x, y, z, tmp);
                b[0] = Math.min(b[0], tmp.x);
                b[1] = Math.min(b[1], tmp.y);
                b[2] = Math.min(b[2], tmp.z);
                b[3] = Math.max(b[3], tmp.x);
                b[4] = Math.max(b[4], tmp.y);
                b[5] = Math.max(b[5], tmp.z);
            }
        }
        for (ModelPart child : part.children.values()) {
            measure(child, ps, b, tmp);
        }
        ps.popPose();
    }
}
