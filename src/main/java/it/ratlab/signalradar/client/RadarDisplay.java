// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.display.RadarMath;
import it.ratlab.signalradar.display.ScreenLayout;
import it.ratlab.signalradar.display.Vec2;
import it.ratlab.signalradar.display.Vec3d;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the CRT display and the status LED as geometry on the radar model (model units, 0..16), fullbright. Quads use
 * {@link RenderType#text} with a white texture (lightmap only, no diffuse shading: the type vanilla uses for maps in
 * hand and in item frames, so shader packs handle it). Every quad is opaque; fades are pre-mixed against the disc
 * colour and layers are separated by small z offsets, so draw order and translucent sorting never matter.
 * <p>
 * Data (blips, text) is shown only in first-person contexts, which only the local player's hands use. Everything else
 * gets the cosmetic sweep; the GUI icon is static.
 */
final class RadarDisplay {
    static final RenderType QUADS = RenderType.text(SignalRadar.id("textures/misc/white.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    // Layers in model units in front of the screen plane: 0.03 apart so they do not z-fight at item-frame distance,
    // all within 0.3 units (still behind the bezel front).
    private static final float L_BG = 0.030f;
    private static final float L_DISC = 0.060f;
    private static final float L_TRAIL = 0.090f;
    private static final float L_RING = 0.120f;
    private static final float L_SWEEP = 0.150f;
    private static final float L_BLIP = 0.180f;
    private static final float L_MARK = 0.210f;
    private static final float L_TEXT_BG = 0.240f;
    private static final float L_TEXT = 0.280f;

    private static final int DISC_SEGMENTS = 48;
    private static final int TRAIL_SEGMENTS = 20;
    private static final double TRAIL_ANGLE = 1.7;
    private static final double STATIC_SWEEP = 0.8;
    private static final double MARGIN = 0.35;
    private static final float TEXT_SCALE = 0.09f;

    enum Mode { STATIC, COSMETIC, LIVE }

    private final Matrix4f matrix;
    private final VertexConsumer vc;
    private final float bright;
    private final float z;

    private RadarDisplay(Matrix4f matrix, VertexConsumer vc, float z) {
        this.matrix = matrix;
        this.vc = vc;
        this.bright = RadarClientConfig.screenBrightness();
        this.z = z;
    }

    static Mode mode(ItemDisplayContext ctx) {
        if (ctx == ItemDisplayContext.GUI) {
            return Mode.STATIC;
        }
        return ctx.firstPerson() && Minecraft.getInstance().player != null ? Mode.LIVE : Mode.COSMETIC;
    }

    /** Called by the item renderer with the pose in item model space (0..1). */
    static void render(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        ScreenLayout lay = RadarScreenLoader.layout();
        Mode mode = mode(ctx);
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        double ticks = mc.level == null ? 0 : mc.level.getGameTime() + partial;
        long nowMs = System.currentTimeMillis();

        int energy = RadarItem.energy(stack);
        int capacity = Math.max(1, SignalRadarConfig.capacity());
        LocalPlayer player = mc.player;
        ScanSnapshot snap = mode == Mode.LIVE ? ClientRadarState.latest() : null;
        if (snap != null && RadarMath.stale(nowMs, ClientRadarState.receivedAtMillis(), snap.refreshSeconds())) {
            snap = null; // no data: sweep only
        }
        boolean noSignal = energy <= 0 || (snap != null && snap.noSignal());
        boolean blink = ((long) ticks / 10) % 2 == 0;

        ps.pushPose();
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        Matrix4f m = ps.last().pose();
        RadarDisplay d = new RadarDisplay(m, buffers.getBuffer(QUADS), (float) lay.z());

        // Geometry of this screen: disc on the left, side panel on the right.
        double h = lay.height();
        double w = lay.width();
        double panelW = Math.max(1.2, w * 0.2);
        double r = Math.max(0.5, Math.min((h - 2 * MARGIN) / 2, (w - 3 * MARGIN - panelW) / 2));
        double cx = lay.x0() + MARGIN + r;
        double cy = lay.y0() + h / 2;
        double px0 = cx + r + MARGIN;
        double px1 = lay.x1() - MARGIN;

        d.rect(lay.x0(), lay.y0(), lay.x1(), lay.y1(), L_BG, RadarColors.SCREEN_BG);
        d.disc(cx, cy, r, L_DISC, RadarColors.DISC);
        double sweep = mode == Mode.STATIC ? STATIC_SWEEP : RadarMath.sweepAngle(ticks);
        // Only live data follows the player's yaw; the cosmetic sweep keeps a fixed north marker.
        float yaw = player != null && mode == Mode.LIVE ? player.getViewYRot(partial) : 180f;

        List<Text> texts = new ArrayList<>();
        if (noSignal && mode != Mode.STATIC) {
            d.noise(cx, cy, r, (long) (ticks / 2));
            if (blink) {
                texts.add(new Text(noSignalText(), cx, cy, 0.1f, RadarColors.NO_SIGNAL_TEXT, true));
            }
        } else {
            d.trail(cx, cy, r, sweep);
            d.rings(cx, cy, r);
            d.line(cx, cy, cx + Math.sin(sweep) * r, cy + Math.cos(sweep) * r, 0.12, L_SWEEP, RadarColors.SWEEP);
            d.northMarker(cx, cy, r, yaw, texts);
            d.tri(cx, cy + 0.28, cx - 0.2, cy - 0.18, cx + 0.2, cy - 0.18, L_MARK, RadarColors.NORTH); // you
            if (mode == Mode.LIVE && snap != null && player != null) {
                d.blips(snap, player, partial, nowMs, cx, cy, r, sweep, yaw);
                if (RaiseState.progress(partial) > 0.5f && RaiseState.arm(player) == armOf(ctx)) {
                    d.raisedLine(snap, player, partial, nowMs, lay, texts);
                }
            }
        }
        d.panel(px0, px1, lay, RadarItem.tier(stack), energy / (double) capacity, snap, texts);
        d.led(lay, noSignal, energy / (double) capacity, blink || mode == Mode.STATIC);

        // Text last: the font uses its own render type, which ends our quad batch.
        Font font = mc.font;
        for (Text t : texts) {
            d.text(font, buffers, ps, t);
        }
        ps.popPose();
    }

    private static net.minecraft.world.entity.HumanoidArm armOf(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? net.minecraft.world.entity.HumanoidArm.LEFT
                : net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    private static String noSignalString;
    private static String noSignalLang;

    /** Translated once per language, not per frame. */
    private static String noSignalText() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (noSignalString == null || !lang.equals(noSignalLang)) {
            noSignalString = Component.translatable("signalradar.display.no_signal").getString();
            noSignalLang = lang;
        }
        return noSignalString;
    }

    private static ScanSnapshot labelSnapshot;
    private static String labelText = "";

    /** Range label, formatted once per snapshot. */
    private static String rangeLabel(ScanSnapshot snap) {
        if (snap != labelSnapshot) {
            labelSnapshot = snap;
            labelText = snap.range() >= 1000 ? String.format("%.1fk", snap.range() / 1000.0) : snap.range() + "m";
        }
        return labelText;
    }

    private record Text(String text, double x, double y, float scale, int color, boolean centered) {}

    // ------------------------------------------------------------------ content

    private void trail(double cx, double cy, double r, double sweep) {
        double step = TRAIL_ANGLE / TRAIL_SEGMENTS;
        for (int i = 0; i < TRAIL_SEGMENTS; i++) {
            double a1 = sweep - i * step;
            double a0 = a1 - step;
            double f = 1.0 - i / (double) TRAIL_SEGMENTS;
            wedge(cx, cy, r, a0, a1, L_TRAIL, RadarMath.mix(RadarColors.DISC, RadarColors.TRAIL, 0.6 * f * f));
        }
    }

    private void rings(double cx, double cy, double r) {
        ring(cx, cy, r / 3, 0.06, L_RING, RadarColors.RING);
        ring(cx, cy, r * 2 / 3, 0.06, L_RING, RadarColors.RING);
        ring(cx, cy, r - 0.06, 0.12, L_RING, RadarColors.RIM);
    }

    private void northMarker(double cx, double cy, double r, float yaw, List<Text> texts) {
        Vec2 n = RadarMath.north(yaw);
        double a = RadarMath.displayAngle(n.x(), n.y());
        double tipR = r - 0.1;
        double baseR = r - 0.6;
        double half = 0.22 / baseR;
        tri(cx + Math.sin(a) * tipR, cy + Math.cos(a) * tipR,
                cx + Math.sin(a - half) * baseR, cy + Math.cos(a - half) * baseR,
                cx + Math.sin(a + half) * baseR, cy + Math.cos(a + half) * baseR, L_MARK, RadarColors.NORTH);
        double lr = r - 1.05;
        texts.add(new Text("N", cx + Math.sin(a) * lr, cy + Math.cos(a) * lr, 0.06f, RadarColors.NORTH, true));
    }

    private void blips(ScanSnapshot snap, LocalPlayer player, float partial, long nowMs, double cx, double cy, double r,
                       double sweep, float yaw) {
        Vec3 pos = player.getPosition(partial);
        boolean heights = RadarClientConfig.showHeightArrows();
        for (Blip b : snap.blips()) {
            Vec3d wp = ClientRadarState.position(b, nowMs);
            Vec2 rel = RadarMath.relative(wp.x() - pos.x, wp.z() - pos.z, yaw);
            RadarMath.Placed p = RadarMath.place(rel, snap.range(), r, b.outOfRange());
            double ang = RadarMath.displayAngle(p.x(), p.y());
            double glow = RadarMath.phosphor(sweep, ang);
            int base = b.color() == 0 ? RadarColors.BLIP_DEFAULT : b.color();
            if (b.found()) {
                base = RadarMath.mix(base, RadarColors.DISC, RadarColors.FOUND_DIM);
            }
            int c = RadarMath.mix(RadarColors.DISC, base, glow);
            double bx = cx + p.x();
            double by = cy + p.y();
            if (p.clamped()) {
                double tipR = r - 0.12;
                double baseR = r - 0.7;
                double half = 0.28 / baseR;
                tri(cx + Math.sin(ang) * tipR, cy + Math.cos(ang) * tipR,
                        cx + Math.sin(ang - half) * baseR, cy + Math.cos(ang - half) * baseR,
                        cx + Math.sin(ang + half) * baseR, cy + Math.cos(ang + half) * baseR, L_BLIP, c);
                bx = cx + Math.sin(ang) * (baseR - 0.1);
                by = cy + Math.cos(ang) * (baseR - 0.1);
            } else {
                rect(bx - 0.21, by - 0.21, bx + 0.21, by + 0.21, L_BLIP, c);
            }
            if (b.found()) {
                int check = RadarMath.mix(RadarColors.DISC, RadarColors.FOUND_CHECK, Math.max(0.5, glow));
                line(bx + 0.28, by + 0.05, bx + 0.42, by - 0.1, 0.08, L_MARK, check);
                line(bx + 0.42, by - 0.1, bx + 0.7, by + 0.3, 0.08, L_MARK, check);
            }
            int hm = heights ? RadarMath.heightMarker(wp.y() - pos.y) : 0;
            if (hm != 0) {
                int hc = RadarMath.mix(RadarColors.DISC, RadarColors.HEIGHT_ARROW, Math.max(0.45, glow));
                double ax = bx - 0.45;
                if (hm > 0) {
                    tri(ax, by + 0.2, ax - 0.16, by - 0.1, ax + 0.16, by - 0.1, L_MARK, hc);
                } else {
                    tri(ax, by - 0.2, ax + 0.16, by + 0.1, ax - 0.16, by + 0.1, L_MARK, hc);
                }
            }
        }
    }

    /** Raised: name (or ???), distance and compass of the blip closest to the view direction. */
    private void raisedLine(ScanSnapshot snap, LocalPlayer player, float partial, long nowMs, ScreenLayout lay, List<Text> texts) {
        if (snap.blips().isEmpty()) {
            return;
        }
        List<Vec3d> pts = new ArrayList<>(snap.blips().size());
        for (Blip b : snap.blips()) {
            pts.add(ClientRadarState.position(b, nowMs));
        }
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        int i = RadarMath.closestToView(new Vec3d(eye.x, eye.y, eye.z), new Vec3d(look.x, look.y, look.z), pts);
        if (i < 0) {
            return;
        }
        Blip b = snap.blips().get(i);
        Vec3d wp = pts.get(i);
        Vec3 pos = player.getPosition(partial);
        double dx = wp.x() - pos.x;
        double dz = wp.z() - pos.z;
        String tail = "  " + RadarMath.metres(dx, dz) + "m " + RadarMath.compass(dx, dz);
        Font font = Minecraft.getInstance().font;
        int maxPx = (int) ((lay.width() - 0.8) / TEXT_SCALE);
        String name = font.plainSubstrByWidth(b.name().getString(), Math.max(10, maxPx - font.width(tail)));
        double y0 = lay.y0() + 0.2;
        double y1 = y0 + 1.1;
        rect(lay.x0() + 0.2, y0, lay.x1() - 0.2, y1, L_TEXT_BG, RadarColors.TEXT_BG);
        texts.add(new Text(name + tail, lay.x0() + 0.4, (y0 + y1) / 2, TEXT_SCALE, RadarColors.TEXT, false));
    }

    private void panel(double px0, double px1, ScreenLayout lay, int tier, double energyFrac, ScanSnapshot snap, List<Text> texts) {
        if (px1 - px0 < 0.6) {
            return;
        }
        double top = lay.y1() - MARGIN;
        double bottom = lay.y0() + MARGIN;
        rect(px0, bottom, px1, top, L_DISC, RadarColors.PANEL_BG);
        // Tier pips: 5 in a row, lit up to the tier.
        int pips = RadarItem.MAX_TIER + 1;
        double gap = 0.12;
        double pw = (px1 - px0 - 0.2 - gap * (pips - 1)) / pips;
        double py1 = top - 0.15;
        double py0 = py1 - Math.min(0.4, pw);
        for (int i = 0; i < pips; i++) {
            double x = px0 + 0.1 + i * (pw + gap);
            rect(x, py0, x + pw, py1, L_RING, i <= tier ? RadarColors.PIP_ON : RadarColors.PIP_OFF);
        }
        // Range label (live data only), then the energy bar filling the rest.
        double barTop = py0 - 0.25;
        if (snap != null) {
            texts.add(new Text(rangeLabel(snap), (px0 + px1) / 2, py0 - 0.5, 0.065f, RadarColors.RANGE_TEXT, true));
            barTop = py0 - 0.9;
        }
        double bw = Math.min(0.9, (px1 - px0) * 0.45);
        double bx0 = (px0 + px1) / 2 - bw / 2;
        double by0 = bottom + 0.15;
        if (barTop - by0 < 0.4) {
            return;
        }
        rect(bx0, by0, bx0 + bw, barTop, L_RING, RadarColors.ENERGY_BAR_BG);
        double f = Math.max(0, Math.min(1, energyFrac));
        int c = f <= 0 ? RadarColors.ENERGY_EMPTY : f < 0.2 ? RadarColors.ENERGY_LOW : RadarColors.ENERGY_OK;
        if (f > 0) {
            rect(bx0 + 0.08, by0 + 0.08, bx0 + bw - 0.08, by0 + 0.08 + (barTop - by0 - 0.16) * f, L_SWEEP, c);
        }
    }

    private void noise(double cx, double cy, double r, long frame) {
        double cell = 0.5;
        int n = (int) Math.ceil(r / cell);
        for (int i = -n; i < n; i++) {
            for (int j = -n; j < n; j++) {
                double x0 = cx + i * cell;
                double y0 = cy + j * cell;
                double mx = x0 + cell / 2 - cx;
                double my = y0 + cell / 2 - cy;
                if (mx * mx + my * my > (r - 0.25) * (r - 0.25)) {
                    continue;
                }
                long hsh = (i * 73856093L) ^ (j * 19349663L) ^ (frame * 83492791L);
                hsh ^= (hsh >>> 13);
                hsh *= 0x9E3779B97F4A7C15L;
                double v = ((hsh >>> 40) & 0xFFFF) / 65535.0;
                rect(x0, y0, x0 + cell, y0 + cell, L_TRAIL, RadarMath.mix(RadarColors.STATIC_DARK, RadarColors.STATIC_LIGHT, v * v));
            }
        }
        ring(cx, cy, r - 0.06, 0.12, L_RING, RadarColors.RING);
    }

    private void led(ScreenLayout lay, boolean noSignal, double energyFrac, boolean blinkOn) {
        int c;
        if (noSignal) {
            c = blinkOn ? RadarColors.LED_NO_SIGNAL : RadarColors.LED_OFF;
        } else {
            c = energyFrac < 0.2 ? RadarColors.LED_LOW : RadarColors.LED_OK;
        }
        float e = 0.03f;
        // South face (+z).
        quad3(lay.ledX0(), lay.ledY0(), lay.ledZ1() + e, lay.ledX1(), lay.ledY0(), lay.ledZ1() + e,
                lay.ledX1(), lay.ledY1(), lay.ledZ1() + e, lay.ledX0(), lay.ledY1(), lay.ledZ1() + e, c);
        // Top face (+y).
        quad3(lay.ledX0(), lay.ledY1() + e, lay.ledZ1(), lay.ledX1(), lay.ledY1() + e, lay.ledZ1(),
                lay.ledX1(), lay.ledY1() + e, lay.ledZ0(), lay.ledX0(), lay.ledY1() + e, lay.ledZ0(), c);
    }

    private void text(Font font, MultiBufferSource buffers, PoseStack ps, Text t) {
        ps.pushPose();
        float width = font.width(t.text());
        float x = (float) t.x() - (t.centered() ? width * t.scale() / 2f : 0f);
        float y = (float) t.y() + font.lineHeight * t.scale() / 2f;
        ps.translate(x, y, z + L_TEXT);
        ps.scale(t.scale(), -t.scale(), t.scale());
        font.drawInBatch(t.text(), 0f, 0f, 0xFF000000 | shade(t.color()), false, ps.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LIGHT);
        ps.popPose();
    }

    // ------------------------------------------------------------------ primitives (model units)

    private int shade(int rgb) {
        if (bright >= 0.999f) {
            return rgb & 0xFFFFFF;
        }
        int r = Math.round(((rgb >> 16) & 0xFF) * bright);
        int g = Math.round(((rgb >> 8) & 0xFF) * bright);
        int b = Math.round((rgb & 0xFF) * bright);
        return (r << 16) | (g << 8) | b;
    }

    private void rect(double x0, double y0, double x1, double y1, float layer, int color) {
        quad(x0, y0, x1, y0, x1, y1, x0, y1, layer, color);
    }

    private void tri(double ax, double ay, double bx, double by, double cx, double cy, float layer, int color) {
        quad(ax, ay, bx, by, cx, cy, cx, cy, layer, color);
    }

    private void line(double x0, double y0, double x1, double y1, double width, float layer, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) {
            return;
        }
        double nx = -dy / len * width / 2;
        double ny = dx / len * width / 2;
        quad(x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, x0 + nx, y0 + ny, layer, color);
    }

    private void wedge(double cx, double cy, double r, double a0, double a1, float layer, int color) {
        tri(cx, cy, cx + Math.sin(a0) * r, cy + Math.cos(a0) * r, cx + Math.sin(a1) * r, cy + Math.cos(a1) * r, layer, color);
    }

    private void disc(double cx, double cy, double r, float layer, int color) {
        double step = RadarMath.TAU / DISC_SEGMENTS;
        for (int i = 0; i < DISC_SEGMENTS; i++) {
            wedge(cx, cy, r, i * step, (i + 1) * step, layer, color);
        }
    }

    private void ring(double cx, double cy, double r, double width, float layer, int color) {
        double step = RadarMath.TAU / DISC_SEGMENTS;
        double ri = r - width / 2;
        double ro = r + width / 2;
        for (int i = 0; i < DISC_SEGMENTS; i++) {
            double a0 = i * step;
            double a1 = a0 + step;
            quad(cx + Math.sin(a0) * ri, cy + Math.cos(a0) * ri, cx + Math.sin(a0) * ro, cy + Math.cos(a0) * ro,
                    cx + Math.sin(a1) * ro, cy + Math.cos(a1) * ro, cx + Math.sin(a1) * ri, cy + Math.cos(a1) * ri, layer, color);
        }
    }

    /** Quad on the screen plane; vertex order is fixed up to counter-clockwise (seen from +z) so culling keeps it. */
    private void quad(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy, float layer, int color) {
        double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) + (cx - ax) * (dy - ay) - (cy - ay) * (dx - ax);
        float zz = z + layer;
        int argb = 0xFF000000 | shade(color);
        if (area >= 0) {
            vertex(ax, ay, zz, argb);
            vertex(bx, by, zz, argb);
            vertex(cx, cy, zz, argb);
            vertex(dx, dy, zz, argb);
        } else {
            vertex(dx, dy, zz, argb);
            vertex(cx, cy, zz, argb);
            vertex(bx, by, zz, argb);
            vertex(ax, ay, zz, argb);
        }
    }

    private void quad3(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                       double dx, double dy, double dz, int color) {
        int argb = 0xFF000000 | shade(color);
        vertex(ax, ay, az, argb);
        vertex(bx, by, bz, argb);
        vertex(cx, cy, cz, argb);
        vertex(dx, dy, dz, argb);
    }

    private void vertex(double x, double y, double zz, int argb) {
        vc.addVertex(matrix, (float) x, (float) y, (float) zz).setColor(argb).setUv(0.5f, 0.5f).setLight(LIGHT);
    }
}
