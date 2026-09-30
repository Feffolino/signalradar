// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.display.BlipLayout;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    private static final float L_BLIP = 0.180f; // dots, and the dark backing of icons
    private static final float L_ICON = 0.210f; // icon content (sprites, flattened items)
    private static final float L_FRAME = 0.240f; // icon frame
    private static final float L_MARK = 0.270f;
    private static final float L_TEXT_BG = 0.300f;
    private static final float L_TEXT = 0.340f;
    /** Icons across the screen width at iconSize 1.0. */
    private static final double ICONS_ACROSS = 12.0;
    /** Item models are squashed on z to this many sub steps per model unit of depth (they stay inside their depth slot). */
    private static final float ITEM_DEPTH = 1.5f;

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
    private final PoseStack ps;
    private final MultiBufferSource buffers;
    /** Side of one icon square, model units. */
    private final double iconSide;
    /** Icons collected while the quad batch is open; drawn afterwards (other textures end the batch). */
    private final List<IconDraw> icons = new ArrayList<>();

    private RadarDisplay(Matrix4f matrix, VertexConsumer vc, float z, PoseStack ps, MultiBufferSource buffers, double screenWidth) {
        this.matrix = matrix;
        this.vc = vc;
        this.bright = RadarClientConfig.screenBrightness();
        this.z = z;
        this.ps = ps;
        this.buffers = buffers;
        this.iconSide = screenWidth / ICONS_ACROSS * RadarClientConfig.iconSize();
    }

    private record IconDraw(RadarIcons.Icon icon, double cx, double cy, double half, float layer, float sub, boolean found) {}

    /** Squared horizontal distance of every blip of the snapshot being drawn (index = blip index), reused between frames. */
    private static double[] distSq = new double[64];
    private static double[] sortedDistSq = new double[64];

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
        double ticks = mc.level == null ? 0 : RadarClock.ticks(partial);
        long nowMs = System.currentTimeMillis();

        int energy = RadarItem.energy(stack);
        int capacity = Math.max(1, RadarItem.capacity(stack));
        LocalPlayer player = mc.player;
        ScanSnapshot snap = mode == Mode.LIVE ? ClientRadarState.latest() : null;
        if (snap != null && RadarMath.stale(nowMs, ClientRadarState.receivedAtMillis(), snap.refreshSeconds())) {
            snap = null; // no data: sweep only
        }
        boolean noBattery = energy <= 0;
        boolean noSignal = !noBattery && snap != null && snap.noSignal();
        boolean blink = ((long) ticks / 10) % 2 == 0;

        ps.pushPose();
        ps.scale(1f / 16f, 1f / 16f, 1f / 16f);
        Matrix4f m = ps.last().pose();
        RadarDisplay d = new RadarDisplay(m, buffers.getBuffer(QUADS), (float) lay.z(), ps, buffers, lay.width());

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
        if (noBattery && mode != Mode.STATIC) {
            // Calm dark screen: battery outline and text, no noise, no blinking text (the LED blinks).
            d.batteryIcon(cx, cy + 0.45);
            texts.add(new Text(noBatteryText(), cx, cy - 0.85, fitScale(mc.font, noBatteryText(), 0.09f, r * 1.7), RadarColors.NO_SIGNAL_TEXT, true));
        } else if (noSignal && mode != Mode.STATIC) {
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
                d.blips(snap, player, partial, nowMs, cx, cy, r, sweep, yaw, ticks, texts);
                if (RaiseState.progress(partial) > 0.5f && RaiseState.arm(player) == armOf(ctx)) {
                    d.raisedLine(snap, player, partial, nowMs, lay, texts);
                }
            }
        }
        d.panel(px0, px1, lay, RadarItem.tier(stack), energy / (double) capacity, snap, texts);
        d.led(lay, noBattery || noSignal, energy / (double) capacity, blink || mode == Mode.STATIC);
        // Icons last among the quads: each texture switch ends the open quad batch.
        d.drawIcons(mc);

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

    private static String noBatteryString;
    private static String noBatteryLang;

    /** Translated once per language, not per frame. */
    private static String noBatteryText() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (noBatteryString == null || !lang.equals(noBatteryLang)) {
            noBatteryString = Component.translatable("signalradar.display.no_battery").getString();
            noBatteryLang = lang;
        }
        return noBatteryString;
    }

    /** Text scale that keeps the string within {@code maxWidth} model units. */
    private static float fitScale(Font font, String text, float scale, double maxWidth) {
        float w = Math.max(1, font.width(text));
        return (float) Math.min(scale, maxWidth / w);
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

    private static int labelRange = -1;
    private static String labelText = "";

    /** Range label, formatted once per displayed range. */
    private static String rangeLabel(int range) {
        if (range != labelRange) {
            labelRange = range;
            labelText = range >= 1000 ? String.format("%.1fk", range / 1000.0) : range + "m";
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

    /** Everything needed to draw one blip, computed once per frame. */
    private record Spot(Blip blip, Vec3d wp, RadarMath.Placed placed, double ang) {}

    private void blips(ScanSnapshot snap, LocalPlayer player, float partial, long nowMs, double cx, double cy, double r,
                       double sweep, float yaw, double ticks, List<Text> texts) {
        Vec3 pos = player.getPosition(partial);
        boolean heights = RadarClientConfig.showHeightArrows();
        List<Blip> list = snap.blips();
        int maxIcons = RadarClientConfig.maxIcons();
        int range = RadarZoom.range(snap.range()); // chosen display range; farther blips sit on the rim with an arrow
        double limit = iconDistanceLimit(list, pos.x, pos.z, maxIcons);

        // Where every blip is on the display, then merge the ones that sit almost on the same spot.
        int n = list.size();
        Spot[] spots = new Spot[n];
        List<BlipLayout.Item> items = new ArrayList<>(n);
        for (int bi = 0; bi < n; bi++) {
            Blip b = list.get(bi);
            int vis = RadarMath.visibility(b.category(), Math.sqrt(distSq[bi]), range, snap.range());
            if (vis == RadarMath.HIDDEN) {
                continue; // local blip beyond the peripheral band: not drawn, not grouped
            }
            Vec3d wp = ClientRadarState.position(b, nowMs);
            Vec2 rel = RadarMath.relative(wp.x() - pos.x, wp.z() - pos.z, yaw);
            RadarMath.Placed p = RadarMath.place(rel, range, r, b.outOfRange() || vis == RadarMath.RIM);
            spots[bi] = new Spot(b, wp, p, RadarMath.displayAngle(p.x(), p.y()));
            items.add(new BlipLayout.Item(bi, p.x(), p.y(), b.found(), distSq[bi], b.id()));
        }
        List<BlipLayout.Group> groups = BlipLayout.group(items, iconSide * BlipLayout.MERGE_FRACTION);
        // Icons go to the most important leads, in the fixed importance order (not in list order).
        RadarIcons.Icon[] iconAt = new RadarIcons.Icon[n];
        int iconsLeft = maxIcons;
        List<BlipLayout.Group> byImportance = new ArrayList<>(groups);
        byImportance.sort((g1, g2) -> BlipLayout.IMPORTANCE.compare(g1.lead(), g2.lead()));
        for (BlipLayout.Group g : byImportance) {
            int bi = g.lead().index();
            Blip b = spots[bi].blip();
            if (iconsLeft > 0 && distSq[bi] <= limit && !b.icon().isEmpty()) {
                RadarIcons.Icon icon = RadarIcons.get(b.icon());
                if (icon.kind() != RadarIcons.Kind.DOT) {
                    iconsLeft--;
                    iconAt[bi] = icon;
                }
            }
        }
        // Every drawn blip gets its own depth slot (fixed id order); everything of a blip sits above the previous one.
        double slot = BlipLayout.slotStep(groups.size(), SLOT_BUDGET, SLOT_STEP);
        float sub = (float) BlipLayout.subStep(slot);
        for (int gi = 0; gi < groups.size(); gi++) {
            BlipLayout.Group g = groups.get(gi);
            int bi = g.lead().index();
            Spot sp = spots[bi];
            Blip b = sp.blip();
            RadarMath.Placed p = sp.placed();
            double ang = sp.ang();
            float base = L_BLIP + (float) (slot * gi);
            RadarIcons.Icon icon = iconAt[bi];
            boolean asIcon = icon != null;
            double hs = asIcon ? iconSide / 2 : 0.21;
            double glow = RadarMath.phosphor(sweep, ang);
            boolean motion = RadarColors.MOTION_CATEGORY.equals(b.category());
            double pulse = 0;
            if (motion) {
                // Motion tracker: red blips that pulse regardless of the sweep.
                pulse = 0.5 + 0.5 * Math.sin(ticks * 0.45);
                glow = Math.max(glow, RadarColors.MOTION_PULSE_MIN + (1 - RadarColors.MOTION_PULSE_MIN) * pulse);
            }
            boolean still = RadarColors.MOTION_STILL_CATEGORY.equals(b.category());
            if (still) {
                // Still hostile: steady dim frame, no pulse.
                glow = 0.7;
            }
            int color = b.color() == 0 ? (motion ? RadarColors.MOTION : still ? RadarColors.MOTION_STILL : RadarColors.BLIP_DEFAULT)
                    : b.color();
            if (b.found()) {
                color = RadarMath.mix(color, RadarColors.DISC, RadarColors.FOUND_DIM);
            }
            int c = RadarMath.mix(RadarColors.DISC, color, asIcon && !motion && !still ? Math.max(glow, ICON_FRAME_MIN) : glow);
            double bx = cx + p.x();
            double by = cy + p.y();
            if (p.clamped()) {
                double tipR = r - 0.12;
                double baseR = r - 0.7;
                double half = 0.28 / baseR;
                tri(cx + Math.sin(ang) * tipR, cy + Math.cos(ang) * tipR,
                        cx + Math.sin(ang - half) * baseR, cy + Math.cos(ang - half) * baseR,
                        cx + Math.sin(ang + half) * baseR, cy + Math.cos(ang + half) * baseR, base + SUB_ARROW * sub, c);
                double inward = asIcon ? baseR - 0.05 - hs : baseR - 0.1;
                bx = cx + Math.sin(ang) * inward;
                by = cy + Math.cos(ang) * inward;
            } else if (!asIcon) {
                double dh = 0.21 + 0.07 * pulse;
                rect(bx - dh, by - dh, bx + dh, by + dh, base + SUB_BACK * sub, c);
            }
            if (asIcon) {
                double t = ICON_FRAME + 0.05 * pulse;
                float fz = base + SUB_FRAME * sub;
                rect(bx - hs, by - hs, bx + hs, by + hs, base + SUB_BACK * sub, RadarColors.ICON_BG);
                rect(bx - hs, by + hs - t, bx + hs, by + hs, fz, c);
                rect(bx - hs, by - hs, bx + hs, by - hs + t, fz, c);
                rect(bx - hs, by - hs + t, bx - hs + t, by + hs - t, fz, c);
                rect(bx + hs - t, by - hs + t, bx + hs, by + hs - t, fz, c);
                icons.add(new IconDraw(icon, bx, by, hs - ICON_FRAME - ICON_INSET, base + SUB_CONTENT * sub, sub, b.found()));
            }
            float mz = base + SUB_MARK * sub;
            if (b.found()) {
                int check = RadarMath.mix(RadarColors.DISC, RadarColors.FOUND_CHECK, Math.max(0.5, glow));
                double k = hs + 0.07;
                line(bx + k, by + 0.05, bx + k + 0.14, by - 0.1, 0.08, mz, check);
                line(bx + k + 0.14, by - 0.1, bx + k + 0.42, by + 0.3, 0.08, mz, check);
            }
            int hm = heights ? RadarMath.heightMarker(sp.wp().y() - pos.y) : 0;
            if (hm != 0) {
                int hc = RadarMath.mix(RadarColors.DISC, RadarColors.HEIGHT_ARROW, Math.max(0.45, glow));
                double ax = bx - (hs + 0.24);
                if (hm > 0) {
                    tri(ax, by + 0.2, ax - 0.16, by - 0.1, ax + 0.16, by - 0.1, mz, hc);
                } else {
                    tri(ax, by - 0.2, ax + 0.16, by + 0.1, ax - 0.16, by + 0.1, mz, hc);
                }
            }
            if (g.count() > 1) {
                // Count badge on the upper right corner of the shown blip (above all blip slots, below the text).
                double bxr = bx + hs;
                double byr = by + hs;
                rect(bxr - 0.2, byr - 0.2, bxr + 0.2, byr + 0.2, L_BADGE, RadarColors.TEXT_BG);
                texts.add(new Text(String.valueOf(g.count()), bxr, byr, 0.06f, RadarColors.TEXT, true));
            }
        }
    }

    // Depth layout of blips (model units): slots between L_BLIP and just below the marks layer, in id order.
    private static final double SLOT_STEP = 0.002;
    private static final double SLOT_BUDGET = 0.085;
    private static final float L_BADGE = 0.295f;
    // Sub-layers inside one slot, in sub steps: rim arrow < backing/dot < content < frame < marks.
    private static final float SUB_ARROW = 0f;
    private static final float SUB_BACK = 1f;
    private static final float SUB_CONTENT = 3f;
    private static final float SUB_FRAME = 4.5f;
    private static final float SUB_MARK = 5.5f;

    private static final double ICON_FRAME = 0.07;
    private static final double ICON_INSET = 0.02;
    private static final double ICON_FRAME_MIN = 0.55;

    /**
     * Fills {@link #distSq} for the blips and returns the squared distance of the {@code max}-th nearest one (everything up
     * to it may be an icon); no allocation once the scratch arrays are big enough.
     */
    private static double iconDistanceLimit(List<Blip> list, double px, double pz, int max) {
        int n = list.size();
        if (distSq.length < n) {
            distSq = new double[n * 2];
            sortedDistSq = new double[n * 2];
        }
        for (int i = 0; i < n; i++) {
            Blip b = list.get(i);
            double dx = b.x() - px;
            double dz = b.z() - pz;
            distSq[i] = dx * dx + dz * dz;
        }
        if (max <= 0) {
            return -1;
        }
        if (n <= max) {
            return Double.MAX_VALUE;
        }
        System.arraycopy(distSq, 0, sortedDistSq, 0, n);
        java.util.Arrays.sort(sortedDistSq, 0, n);
        return sortedDistSq[max - 1];
    }

    /** Draws the collected icons: sprites as textured quads (fullbright text type), items flattened on z. */
    private void drawIcons(Minecraft mc) {
        if (icons.isEmpty()) {
            return;
        }
        icons.sort(java.util.Comparator.comparingInt((IconDraw i) -> i.icon().sortKey()));
        for (IconDraw d : icons) {
            RadarIcons.Icon icon = d.icon();
            float zz = z + d.layer();
            if (icon.kind() == RadarIcons.Kind.SPRITE) {
                int v = Math.round(255f * bright * (d.found() ? 0.4f : 1f));
                int argb = 0xFF000000 | (v << 16) | (v << 8) | v;
                float h = (float) d.half();
                float layerZ = zz;
                for (RadarIcons.Layer l : icon.layers()) {
                    VertexConsumer c = buffers.getBuffer(l.type());
                    float x0 = (float) d.cx() - h;
                    float x1 = (float) d.cx() + h;
                    float y0 = (float) d.cy() - h;
                    float y1 = (float) d.cy() + h;
                    c.addVertex(matrix, x0, y0, layerZ).setColor(argb).setUv(l.u0(), l.v1()).setLight(LIGHT);
                    c.addVertex(matrix, x1, y0, layerZ).setColor(argb).setUv(l.u1(), l.v1()).setLight(LIGHT);
                    c.addVertex(matrix, x1, y1, layerZ).setColor(argb).setUv(l.u1(), l.v0()).setLight(LIGHT);
                    c.addVertex(matrix, x0, y1, layerZ).setColor(argb).setUv(l.u0(), l.v0()).setLight(LIGHT);
                    layerZ += d.sub() * 0.6f; // hat layer of skins sits just in front of the face
                }
            } else if (icon.kind() == RadarIcons.Kind.HEAD && icon.head() != null) {
                int v = Math.round(255f * bright * (d.found() ? 0.4f : 1f));
                ps.pushPose();
                ps.translate(d.cx(), d.cy(), zz);
                MobFaces.draw(icon.head(), ps, buffers.getBuffer(icon.head().type()), (float) (d.half() * 2), d.sub() * ITEM_DEPTH,
                        0xFF000000 | (v << 16) | (v << 8) | v, LIGHT);
                ps.popPose();
            } else if (icon.kind() == RadarIcons.Kind.ITEM && icon.stack() != null) {
                float side = (float) (d.half() * 2);
                ps.pushPose();
                ps.translate(d.cx(), d.cy(), zz);
                ps.scale(side, side, d.sub() * ITEM_DEPTH);
                int light = d.found() ? LightTexture.pack(3, 3) : LIGHT;
                mc.getItemRenderer().renderStatic(icon.stack(), ItemDisplayContext.GUI, light, OverlayTexture.NO_OVERLAY, ps, buffers,
                        mc.level, 0);
                ps.popPose();
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
            texts.add(new Text(rangeLabel(RadarZoom.range(snap.range())), (px0 + px1) / 2, py0 - 0.5, 0.065f, RadarColors.RANGE_TEXT, true));
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

    /** Empty battery outline with a red sliver, centred on (cx, cy). */
    private void batteryIcon(double cx, double cy) {
        double w = 1.7;
        double h = 0.9;
        double t = 0.11;
        int c = RadarColors.NO_SIGNAL_TEXT;
        double x0 = cx - w / 2 - 0.08;
        double x1 = x0 + w;
        double y0 = cy - h / 2;
        double y1 = cy + h / 2;
        rect(x0, y1 - t, x1, y1, L_MARK, c);
        rect(x0, y0, x1, y0 + t, L_MARK, c);
        rect(x0, y0 + t, x0 + t, y1 - t, L_MARK, c);
        rect(x1 - t, y0 + t, x1, y1 - t, L_MARK, c);
        rect(x1, cy - 0.17, x1 + 0.16, cy + 0.17, L_MARK, c); // terminal
        rect(x0 + t + 0.07, y0 + t + 0.07, x0 + t + 0.19, y1 - t - 0.07, L_MARK, RadarColors.ENERGY_EMPTY); // last sliver
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
