// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.progress;

import it.ratlab.signalradar.api.RadarTargetFoundEvent;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModSounds;
import it.ratlab.signalradar.scan.Locators;
import it.ratlab.signalradar.scan.PlayerProgress;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Found targets and the last death marker.
 *
 * <p>Once per second, for players carrying a radar anywhere in their inventory: every visible target whose
 * {@code min_tier} the best carried radar reaches and whose position is known from caches (never a lookup) is found
 * when the player is within {@code found_radius} blocks of it, measured horizontally (structure positions have no
 * reliable height). The last death marker is cleared when the player is within {@link #LAST_DEATH_CLEAR_DISTANCE}
 * blocks of it (3D).
 */
public final class FoundHandler {
    /** Blocks (3D) from the last death at which the marker is cleared. */
    public static final double LAST_DEATH_CLEAR_DISTANCE = 8.0;
    /** Ticks between two checks of one player. */
    public static final int CHECK_INTERVAL = 20;

    private FoundHandler() {}

    public static void register(IEventBus bus) {
        bus.addListener(FoundHandler::onPlayerTick);
        bus.addListener(EventPriority.LOWEST, FoundHandler::onDeath);
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
            check(player);
        }
    }

    /** Runs for uncancelled deaths only (a totem cancels the event). */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordDeath(player);
        }
    }

    public static void recordDeath(ServerPlayer player) {
        PlayerData.get(player).withLastDeath(Optional.of(new PlayerData.DeathPoint(player.level().dimension().location(), player.blockPosition())))
                .set(player);
    }

    /** Highest tier among the radars in the inventory (main, hotbar, offhand), or -1 without one. */
    public static int carriedTier(Player player) {
        Inventory inv = player.getInventory();
        int best = -1;
        for (ItemStack s : inv.items) {
            if (s.getItem() instanceof RadarItem) {
                best = Math.max(best, RadarItem.tier(s));
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s.getItem() instanceof RadarItem) {
                best = Math.max(best, RadarItem.tier(s));
            }
        }
        return best;
    }

    /** One check (the schedule is the caller's). Returns the number of targets found by it. */
    public static int check(ServerPlayer player) {
        int tier = carriedTier(player);
        if (tier < 0) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        clearLastDeathIfClose(player);
        PlayerProgress progress = PlayerProgress.of(player);
        int found = 0;
        for (TargetDef def : TargetManager.all()) {
            if (def.minTier() > tier || PlayerData.get(player).isFound(def.id()) || !RadarScanner.visible(def, progress)) {
                continue;
            }
            Optional<Vec3> pos = Locators.peek(player, def, def.foundRadius(), level);
            if (pos.isPresent() && horizontalSq(player, pos.get()) <= (double) def.foundRadius() * def.foundRadius()) {
                found(player, def);
                found++;
            }
        }
        return found;
    }

    private static double horizontalSq(ServerPlayer p, Vec3 v) {
        double dx = p.getX() - v.x;
        double dz = p.getZ() - v.z;
        return dx * dx + dz * dz;
    }

    private static void clearLastDeathIfClose(ServerPlayer player) {
        PlayerData data = PlayerData.get(player);
        data.lastDeath().filter(d -> d.dimension().equals(player.level().dimension().location())
                && Vec3.atCenterOf(d.pos()).distanceToSqr(player.position()) <= LAST_DEATH_CLEAR_DISTANCE * LAST_DEATH_CLEAR_DISTANCE)
                .ifPresent(d -> data.withLastDeath(Optional.empty()).set(player));
    }

    /** Marks the target found, gives the stage, posts the event, then sound and action bar. */
    public static void found(ServerPlayer player, TargetDef def) {
        PlayerData.get(player).withFound(def.id(), true).set(player);
        StageHelper.add(player, StageHelper.foundStage(def.id()));
        MinecraftForge.EVENT_BUS.post(new RadarTargetFoundEvent(player, def.id(), def));
        player.playNotifySound(ModSounds.TARGET_FOUND.get(), SoundSource.PLAYERS, 1f, 1f);
        player.displayClientMessage(Component.translatable("message.signalradar.found", def.name()), true);
    }
}
