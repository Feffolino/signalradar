// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.api;

import it.ratlab.signalradar.icon.IconSpec;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanMath;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Posted on the Forge event bus (server side) for every scan that has a signal, after the blips are computed and
 * before the snapshot is sent to the player. The energy is already charged. {@link #getTargets()} is mutable: remove
 * entries or add new ones ({@link #addTarget}). Cancelling it sends NO SIGNAL (no blips) for this scan instead; the
 * energy stays spent. Also posted for the free refreshes between two charged scans (addon refresh intervals).
 */
@Cancelable
public class RadarScanEvent extends PlayerEvent {
    /** Category of blips added through {@link #addTarget}. */
    public static final String SCRIPT_CATEGORY = "script";

    private final ItemStack radar;
    private final int range;
    private final List<Blip> targets;

    public RadarScanEvent(ServerPlayer player, ItemStack radar, int range, List<Blip> blips) {
        super(player);
        this.radar = radar;
        this.range = range;
        this.targets = new ArrayList<>(blips);
    }

    public ServerPlayer getPlayer() {
        return (ServerPlayer) getEntity();
    }

    /** The scanning radar (read only is advised: its energy was already charged). */
    public ItemStack getRadar() {
        return radar;
    }

    public int getTier() {
        return RadarItem.tier(radar);
    }

    /** Display range of this radar in blocks. */
    public int getRange() {
        return range;
    }

    /** The blips that will be sent, in order. Mutable. */
    public List<Blip> getTargets() {
        return targets;
    }

    /** Removes every blip matching the filter; returns how many were removed. */
    public int removeTargets(Predicate<Blip> filter) {
        int before = targets.size();
        targets.removeIf(filter);
        return before - targets.size();
    }

    /**
     * Removes the blip with this id, or every hit of an addon when {@code id} is an addon id (addon blips are
     * {@code <addon id>/<hit key>}). Returns how many were removed.
     */
    public int removeTarget(String id) {
        return removeTargets(b -> b.id().equals(id) || b.id().startsWith(id + "/"));
    }

    /** Removes every blip of a category ({@code narrative}, {@code last_death}, an addon category ...). */
    public int removeCategory(String category) {
        return removeTargets(b -> b.category().equals(category));
    }

    /**
     * Adds a blip at an exact world position (no fuzz), name always shown, category {@link #SCRIPT_CATEGORY}, id
     * {@code script/<name>} (a {@code #n} suffix keeps ids unique within one scan). Out of range when farther than the
     * radar's range.
     */
    public Blip addTarget(Component name, int color, double x, double y, double z) {
        String base = "script/" + name.getString();
        String id = base;
        for (int n = 2; hasId(id); n++) {
            id = base + "#" + n;
        }
        double dist = ScanMath.distance(getEntity().position(), new Vec3(x, y, z));
        Blip b = new Blip(id, SCRIPT_CATEGORY, color & 0xFFFFFF, x, y, z, name, dist > range, false, IconSpec.DEFAULT_TARGET);
        targets.add(b);
        return b;
    }

    private boolean hasId(String id) {
        for (Blip b : targets) {
            if (b.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** True when a listener changed the list (compared by content). */
    public boolean targetsChanged(List<Blip> original) {
        return !targets.equals(original);
    }
}
