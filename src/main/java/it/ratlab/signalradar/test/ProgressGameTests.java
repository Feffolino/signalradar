// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.api.RadarTargetFoundEvent;
import it.ratlab.signalradar.api.SignalRadarAPI;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.progress.FoundHandler;
import it.ratlab.signalradar.progress.PlayerData;
import it.ratlab.signalradar.progress.StageHelper;
import it.ratlab.signalradar.registry.ModAttachments;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.PlayerProgress;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.scan.ScanSettings;
import it.ratlab.signalradar.scan.ScanSnapshot;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.Locator;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 5 checks: attachment, visibility rules, stages, found, last death, commands, API. */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class ProgressGameTests {
    private static final String EMPTY = "gametest_empty";
    private static final ScanSettings DEFAULTS = new ScanSettings(
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_RANGE, SignalRadarConfig.DEFAULT_RANGE, "range"),
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_FUZZ, SignalRadarConfig.DEFAULT_FUZZ, "fuzz"), 50, 5);
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");

    private ProgressGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(ProgressGameTests.class));
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        return p;
    }

    private static ItemStack radar(int tier) {
        ItemStack r = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(r, tier);
        RadarItem.setEnergy(r, 10000);
        return r;
    }

    /** A pos target at the absolute block. */
    private static TargetDef target(String path, BlockPos pos, int minTier, String stage, boolean unlock, int foundRadius, boolean hide) {
        return new TargetDef(SignalRadar.id(path), Component.literal("Name " + path), "narrative", minTier, 0x7CFC00, 128, stage, unlock,
                foundRadius, hide, new Locator.Pos(pos, OVERWORLD));
    }

    private static void withTargets(List<TargetDef> defs, Runnable body) {
        Map<ResourceLocation, TargetDef> before = new TreeMap<>();
        TargetManager.all().forEach(d -> before.put(d.id(), d));
        Map<ResourceLocation, TargetDef> now = new TreeMap<>();
        defs.forEach(d -> now.put(d.id(), d));
        TargetManager.setForTest(now);
        try {
            body.run();
        } finally {
            TargetManager.setForTest(before);
        }
    }

    private static ScanSnapshot scan(ServerPlayer p, int tier) {
        ItemStack r = radar(tier);
        return RadarScanner.scan(r, p.getUUID(), p.position(), 100, DEFAULTS, TargetManager.all(),
                d -> Optional.of(Vec3.atCenterOf(((Locator.Pos) d.locator()).pos())), List.of(), a -> List.of(), RadarScanner.Charge.PAY,
                PlayerProgress.of(p));
    }

    private static Blip blip(ScanSnapshot s, String id) {
        return s.blips().stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }

    private static boolean fails(CommandDispatcher<CommandSourceStack> d, String cmd, CommandSourceStack src) {
        try {
            d.execute(cmd, src);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    /** Subscribes a counter to found events for the duration of {@code body}. */
    private static int countFound(Consumer<AtomicInteger> body) {
        AtomicInteger n = new AtomicInteger();
        Consumer<RadarTargetFoundEvent> l = e -> n.incrementAndGet();
        NeoForge.EVENT_BUS.addListener(l);
        try {
            body.accept(n);
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        return n.get();
    }

    // ------------------------------------------------------------------ attachment

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void attachmentIsCopiedOnDeathAndSurvivesTheCodec(GameTestHelper h) {
        ServerPlayer old = player(h);
        ResourceLocation a = SignalRadar.id("a");
        ResourceLocation b = SignalRadar.id("b");
        PlayerData data = PlayerData.EMPTY.withUnlocked(a, true).withFound(b, true)
                .withLastDeath(Optional.of(new PlayerData.DeathPoint(OVERWORLD, new BlockPos(1, 2, 3))));
        data.set(old);
        // what PlayerList.respawn does with a fresh player entity
        ServerPlayer fresh = player(h);
        h.assertTrue(PlayerData.get(fresh).equals(PlayerData.EMPTY), "fresh player is not empty");
        fresh.restoreFrom(old, false);
        h.assertTrue(PlayerData.get(fresh).equals(data), "not copied on death: " + PlayerData.get(fresh));
        // NBT round trip (what is saved)
        var json = PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        PlayerData back = PlayerData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        h.assertTrue(back.equals(data), "codec round trip: " + back);
        h.assertTrue(PlayerData.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow().equals(PlayerData.EMPTY),
                "empty object is not the empty data");
        h.succeed();
    }

    // ------------------------------------------------------------------ visibility

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void requiresUnlockHidesUntilUnlocked(GameTestHelper h) {
        ServerPlayer p = player(h);
        TargetDef d = target("gated", h.absolutePos(new BlockPos(5, 1, 0)), 0, null, true, 24, false);
        withTargets(List.of(d), () -> {
            h.assertTrue(blip(scan(p, 0), d.blipId()) == null, "locked target visible");
            SignalRadarAPI.unlock(p, d.id());
            h.assertTrue(SignalRadarAPI.isUnlocked(p, d.id()), "isUnlocked");
            h.assertTrue(blip(scan(p, 0), d.blipId()) != null, "unlocked target hidden");
            SignalRadarAPI.lock(p, d.id());
            h.assertTrue(blip(scan(p, 0), d.blipId()) == null, "locked again but visible");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void requiresStageUsesScoreboardTagsWithoutKubeJS(GameTestHelper h) {
        ServerPlayer p = player(h);
        TargetDef d = target("staged", h.absolutePos(new BlockPos(5, 1, 0)), 0, "test_era", false, 24, false);
        withTargets(List.of(d), () -> {
            h.assertTrue(blip(scan(p, 0), d.blipId()) == null, "staged target visible without the stage");
            StageHelper.add(p, "test_era");
            h.assertTrue(p.getTags().contains("test_era"), "stage is not a scoreboard tag");
            h.assertTrue(StageHelper.has(p, "test_era"), "StageHelper.has");
            h.assertTrue(blip(scan(p, 0), d.blipId()) != null, "staged target hidden with the stage");
            StageHelper.remove(p, "test_era");
            h.assertTrue(blip(scan(p, 0), d.blipId()) == null, "visible after removing the stage");
        });
        h.assertTrue(StageHelper.foundStage(SignalRadar.id("a/b")).equals("signalradar_found_a_b"), "found stage name");
        h.succeed();
    }

    // ------------------------------------------------------------------ found

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void foundFiresOnceAddsTheStageAndFlagsTheBlip(GameTestHelper h) {
        ServerPlayer p = player(h);
        TargetDef d = target("close", h.absolutePos(new BlockPos(5, 1, 0)), 1, null, false, 24, false);
        withTargets(List.of(d), () -> {
            // no radar, then a radar of too low a tier: nothing is found
            h.assertTrue(FoundHandler.check(p) == 0, "found without a radar");
            p.getInventory().setItem(20, radar(0));
            h.assertTrue(FoundHandler.check(p) == 0 && !SignalRadarAPI.isFound(p, d.id()), "found with a tier 0 radar (min tier 1)");
            // any slot of the inventory counts, not only the hands
            p.getInventory().setItem(21, radar(1));
            int events = countFound(n -> {
                h.assertTrue(FoundHandler.check(p) == 1, "not found");
                h.assertTrue(FoundHandler.check(p) == 0, "found twice");
                h.assertTrue(FoundHandler.check(p) == 0, "found three times");
            });
            h.assertTrue(events == 1, "event fired " + events + " times");
            h.assertTrue(SignalRadarAPI.isFound(p, d.id()), "flag not set");
            h.assertTrue(p.getTags().contains("signalradar_found_close"), "stage not added: " + p.getTags());
            Blip b = blip(scan(p, 1), d.blipId());
            h.assertTrue(b != null && b.found(), "found blip is not flagged (hide_when_found = false)");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void foundRadiusIsHorizontalAndRespectsVisibility(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().setItem(0, radar(0));
        TargetDef far = target("far", h.absolutePos(new BlockPos(30, 1, 0)), 0, null, false, 24, false);
        TargetDef high = target("high", h.absolutePos(new BlockPos(0, 100, 5)), 0, null, false, 24, false);
        TargetDef gated = target("gated", h.absolutePos(new BlockPos(2, 1, 0)), 0, null, true, 24, false);
        withTargets(List.of(far, high, gated), () -> {
            h.assertTrue(FoundHandler.check(p) == 1, "expected only the high target (horizontal distance 5)");
            h.assertTrue(SignalRadarAPI.isFound(p, high.id()), "high target (other Y) not found");
            h.assertTrue(!SignalRadarAPI.isFound(p, far.id()), "far target found");
            h.assertTrue(!SignalRadarAPI.isFound(p, gated.id()), "locked target found");
            SignalRadarAPI.unlock(p, gated.id());
            h.assertTrue(FoundHandler.check(p) == 1 && SignalRadarAPI.isFound(p, gated.id()), "unlocked target not found");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void foundCheckNeverQueuesStructureLookups(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().setItem(0, radar(0));
        TargetDef d = new TargetDef(SignalRadar.id("struct"), Component.literal("S"), "narrative", 0, 0x7CFC00, 128, null, false, 24, false,
                new Locator.Structure(ResourceLocation.withDefaultNamespace("village_plains"), false, 10));
        withTargets(List.of(d), () -> {
            int before = StructureLookupService.INSTANCE.pending();
            h.assertTrue(FoundHandler.check(p) == 0, "found an unresolved structure");
            h.assertTrue(StructureLookupService.INSTANCE.pending() == before, "the check queued a structure lookup");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void hideWhenFoundRemovesTheBlip(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().setItem(0, radar(0));
        TargetDef d = target("hidden", h.absolutePos(new BlockPos(3, 1, 0)), 0, null, false, 24, true);
        withTargets(List.of(d), () -> {
            h.assertTrue(blip(scan(p, 0), d.blipId()) != null, "not visible before it is found");
            h.assertTrue(FoundHandler.check(p) == 1, "not found");
            h.assertTrue(blip(scan(p, 0), d.blipId()) == null, "hide_when_found target still shown");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void resetFoundClearsFlagAndStageAndAllowsAnotherFind(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getInventory().setItem(0, radar(0));
        TargetDef d1 = target("one", h.absolutePos(new BlockPos(3, 1, 0)), 0, null, false, 24, false);
        TargetDef d2 = target("two", h.absolutePos(new BlockPos(-3, 1, 0)), 0, null, false, 24, false);
        withTargets(List.of(d1, d2), () -> {
            int events = countFound(n -> {
                h.assertTrue(FoundHandler.check(p) == 2, "both targets should be found");
                SignalRadarAPI.resetFound(p, d1.id());
                h.assertTrue(!SignalRadarAPI.isFound(p, d1.id()) && SignalRadarAPI.isFound(p, d2.id()), "reset one");
                h.assertTrue(!p.getTags().contains("signalradar_found_one") && p.getTags().contains("signalradar_found_two"), "stage of reset target");
                h.assertTrue(FoundHandler.check(p) == 1, "the reset target is not found again");
                SignalRadarAPI.resetAllFound(p);
                h.assertTrue(PlayerData.get(p).found().isEmpty() && !p.getTags().contains("signalradar_found_two"), "reset all");
            });
            h.assertTrue(events == 3, "events " + events);
        });
        h.succeed();
    }

    // ------------------------------------------------------------------ last death

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void lastDeathIsRecordedShownAndClearedNearby(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos deathSpot = h.absolutePos(new BlockPos(40, 1, 0));
        p.setPos(Vec3.atBottomCenterOf(deathSpot));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().generic()));
        Optional<PlayerData.DeathPoint> death = SignalRadarAPI.getLastDeath(p);
        h.assertTrue(death.isPresent() && death.get().pos().equals(deathSpot) && death.get().dimension().equals(OVERWORLD),
                "death not recorded: " + death);
        // respawned elsewhere
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        Blip b = blip(scan(p, 0), RadarScanner.LAST_DEATH_ID);
        h.assertTrue(b != null && b.category().equals("last_death") && b.color() == RadarScanner.LAST_DEATH_COLOR, "no last death blip");
        h.assertTrue(b.color() == it.ratlab.signalradar.client.RadarColors.LAST_DEATH, "server and client colour differ");
        // a death in another dimension is not shown here
        PlayerData keep = PlayerData.get(p);
        keep.withLastDeath(Optional.of(new PlayerData.DeathPoint(ResourceLocation.withDefaultNamespace("the_nether"), deathSpot))).set(p);
        h.assertTrue(PlayerProgress.of(p).lastDeath() == null && blip(scan(p, 0), RadarScanner.LAST_DEATH_ID) == null,
                "blip for a death in another dimension");
        keep.set(p);
        // without a radar the marker stays; carrying one, far away, it stays; within 8 blocks it goes
        h.assertTrue(FoundHandler.check(p) == 0 && SignalRadarAPI.getLastDeath(p).isPresent(), "cleared without a radar");
        p.getInventory().setItem(0, radar(0));
        FoundHandler.check(p);
        h.assertTrue(SignalRadarAPI.getLastDeath(p).isPresent(), "cleared while far away");
        p.setPos(Vec3.atBottomCenterOf(deathSpot.offset(7, 0, 0)));
        FoundHandler.check(p);
        h.assertTrue(SignalRadarAPI.getLastDeath(p).isEmpty(), "not cleared within 8 blocks");
        h.assertTrue(blip(scan(p, 0), RadarScanner.LAST_DEATH_ID) == null, "blip after clearing");
        h.succeed();
    }

    // ------------------------------------------------------------------ commands and API

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void commandsUnlockLockResetFoundAndTargets(GameTestHelper h) throws Exception {
        ServerPlayer p = player(h);
        p.getInventory().setItem(0, radar(0));
        TargetDef d = target("cmd", h.absolutePos(new BlockPos(3, 1, 0)), 0, "cmd_stage", true, 24, false);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2);
        withTargets(List.of(d), () -> {
            try {
                dispatcher.execute("signalradar unlock @s signalradar:cmd", src);
                h.assertTrue(SignalRadarAPI.isUnlocked(p, d.id()), "unlock command");
                dispatcher.execute("signalradar lock @s signalradar:cmd", src);
                h.assertTrue(!SignalRadarAPI.isUnlocked(p, d.id()), "lock command");
                h.assertTrue(fails(dispatcher, "signalradar unlock @s signalradar:nope", src), "unknown target accepted");
                dispatcher.execute("signalradar unlock @s signalradar:cmd", src);
                StageHelper.add(p, "cmd_stage");
                h.assertTrue(FoundHandler.check(p) == 1, "not found");
                dispatcher.execute("signalradar targets @s", src);
                dispatcher.execute("signalradar resetfound @s signalradar:cmd", src);
                h.assertTrue(!SignalRadarAPI.isFound(p, d.id()) && !p.getTags().contains("signalradar_found_cmd"), "resetfound target");
                h.assertTrue(FoundHandler.check(p) == 1, "not found again");
                dispatcher.execute("signalradar resetfound @s all", src);
                h.assertTrue(PlayerData.get(p).found().isEmpty() && !p.getTags().contains("signalradar_found_cmd"), "resetfound all");
                h.assertTrue(fails(dispatcher, "signalradar resetfound @s signalradar:nope", src), "resetfound unknown accepted");
                // needs op level 2
                h.assertTrue(fails(dispatcher, "signalradar unlock @s signalradar:cmd", p.createCommandSourceStack().withPermission(0)),
                        "unlock without permission");
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void apiFacadeExposesTargetsAndRadarStacks(GameTestHelper h) {
        ServerPlayer p = player(h);
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 0));
        TargetDef d = target("api", at, 0, null, false, 24, false);
        withTargets(List.of(d), () -> {
            h.assertTrue(SignalRadarAPI.targetIds().equals(java.util.Set.of(d.id())), "targetIds " + SignalRadarAPI.targetIds());
            Vec3 pos = SignalRadarAPI.getTargetPos(h.getLevel(), d.id());
            h.assertTrue(pos != null && pos.equals(Vec3.atCenterOf(at)), "getTargetPos(level) " + pos);
            h.assertTrue(SignalRadarAPI.getTargetPos(p, d.id()) != null, "getTargetPos(player)");
            h.assertTrue(SignalRadarAPI.getTargetPos(h.getLevel(), SignalRadar.id("nope")) == null, "unknown target has a position");
        });
        ItemStack r = radar(0);
        SignalRadarAPI.setTier(r, 2);
        SignalRadarAPI.setEnergy(r, 1234);
        SignalRadarAPI.setAddons(r, List.of(SignalRadar.id("addon_ore")));
        h.assertTrue(SignalRadarAPI.isRadar(r) && SignalRadarAPI.getTier(r) == 2 && SignalRadarAPI.getEnergy(r) == 1234, "tier/energy");
        h.assertTrue(SignalRadarAPI.hasAddon(r, SignalRadar.id("addon_ore")) && !SignalRadarAPI.hasAddon(r, SignalRadar.id("addon_motion")),
                "addons " + SignalRadarAPI.getAddons(r));
        h.succeed();
    }
}
