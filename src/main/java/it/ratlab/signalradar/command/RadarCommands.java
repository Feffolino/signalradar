// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import it.ratlab.signalradar.scan.Locators;
import it.ratlab.signalradar.scan.ScanHandler;
import it.ratlab.signalradar.scan.ScanSettings;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /signalradar} (op level 2): settier, charge, targets, clearcache. */
public final class RadarCommands {
    private static final SimpleCommandExceptionType NO_RADAR = new SimpleCommandExceptionType(Component.translatable("command.signalradar.no_radar"));

    private RadarCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("signalradar").requires(s -> s.hasPermission(2))
                .then(Commands.literal("settier").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, RadarItem.MAX_TIER)).executes(RadarCommands::setTier))))
                .then(Commands.literal("charge").then(Commands.argument("player", EntityArgument.player()).executes(RadarCommands::charge)))
                .then(Commands.literal("targets")
                        .executes(c -> targets(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> targets(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("clearcache").executes(RadarCommands::clearCache)));
    }

    private static ItemStack radarOf(ServerPlayer p) throws CommandSyntaxException {
        ItemStack held = ScanHandler.heldRadar(p);
        if (held.isEmpty()) {
            throw NO_RADAR.create();
        }
        return held;
    }

    private static int setTier(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        ItemStack radar = radarOf(p);
        int tier = IntegerArgumentType.getInteger(c, "tier");
        RadarItem.setTier(radar, tier);
        c.getSource().sendSuccess(() -> Component.translatable("command.signalradar.settier", p.getDisplayName(), tier), true);
        return tier;
    }

    private static int charge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        ItemStack radar = radarOf(p);
        RadarItem.setEnergy(radar, SignalRadarConfig.capacity());
        c.getSource().sendSuccess(() -> Component.translatable("command.signalradar.charge", p.getDisplayName(), SignalRadarConfig.capacity()), true);
        return SignalRadarConfig.capacity();
    }

    private static int targets(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        CommandSourceStack src = c.getSource();
        ItemStack radar = ScanHandler.heldRadar(p);
        int tier = radar.isEmpty() ? RadarItem.MAX_TIER : RadarItem.tier(radar);
        int range = ScanSettings.fromConfig().range(tier);
        long now = p.level().getGameTime();
        int count = 0;
        for (TargetDef def : TargetManager.all()) {
            count++;
            Optional<Vec3> pos = Locators.locate(p, def, range, p.serverLevel(), now);
            Component where = pos.<Component>map(v -> Component.literal(String.format("%.0f %.0f %.0f", v.x, v.y, v.z)))
                    .orElseGet(() -> Component.translatable("command.signalradar.targets.none"));
            src.sendSuccess(() -> Component.translatable("command.signalradar.targets.entry", def.id().toString(), def.locator().key(),
                    def.minTier(), where), false);
        }
        StructureCacheData data = StructureCacheData.get(src.getServer());
        data.entries().forEach((k, e) -> src.sendSuccess(() -> Component.translatable("command.signalradar.targets.cached", k,
                e.pos() == null ? Component.translatable("command.signalradar.targets.miss")
                        : Component.literal(e.pos().getX() + " " + e.pos().getY() + " " + e.pos().getZ())), false));
        int pending = StructureLookupService.INSTANCE.pending();
        int finalCount = count;
        src.sendSuccess(() -> Component.translatable("command.signalradar.targets.summary", finalCount, data.entries().size(), pending), false);
        return count;
    }

    private static int clearCache(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        StructureCacheData data = StructureCacheData.get(src.getServer());
        int n = data.entries().size();
        data.clear();
        StructureLookupService.INSTANCE.clearQueue();
        BlockLocatorScan.INSTANCE.clear();
        src.sendSuccess(() -> Component.translatable("command.signalradar.clearcache", n), true);
        return n;
    }
}
