// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.net.SnapshotPayload;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import it.ratlab.signalradar.scan.Locators;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.scan.ScanHandler;
import it.ratlab.signalradar.scan.ScanMath;
import it.ratlab.signalradar.scan.ScanSettings;
import it.ratlab.signalradar.scan.ScanSnapshot;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.Locator;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import it.ratlab.signalradar.target.TargetParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import it.ratlab.signalradar.item.TwoHanded;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Phase 2 checks: targets, scan rules, structure queue, locators, payload, commands. */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScanGameTests {
    private static final String EMPTY = "gametest_empty";
    private static final ScanSettings DEFAULTS = new ScanSettings(
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_RANGE, SignalRadarConfig.DEFAULT_RANGE, "range"),
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_FUZZ, SignalRadarConfig.DEFAULT_FUZZ, "fuzz"), 50, 5);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000abcd");

    private ScanGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(ScanGameTests.class));
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack radar(int tier, int energy) {
        ItemStack r = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(r, tier);
        RadarItem.setEnergy(r, energy);
        return r;
    }

    private static TargetDef def(String path, int minTier, int reveal) {
        return new TargetDef(SignalRadar.id(path), Component.literal("Name " + path), "narrative", minTier, 0x7CFC00, reveal, null, false,
                24, false, new Locator.Pos(BlockPos.ZERO, ResourceLocation.withDefaultNamespace("overworld")));
    }

    /** Locator that places each target at (x, 64, 0) from a table. */
    private static java.util.function.Function<TargetDef, Optional<Vec3>> at(Map<String, Double> xById) {
        return d -> Optional.ofNullable(xById.get(d.id().getPath())).map(x -> new Vec3(x, 64, 0));
    }

    private static ScanSnapshot scan(ItemStack radar, long now, List<TargetDef> defs, Map<String, Double> xs) {
        return RadarScanner.scan(radar, PLAYER, new Vec3(0, 64, 0), now, DEFAULTS, defs, at(xs));
    }

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    private static final ResourceLocation ID = SignalRadar.id("t");

    // ------------------------------------------------------------------ target JSON

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void targetParsesFullSchema(GameTestHelper h) {
        Optional<TargetDef> o = TargetParser.parse(ID, json("""
                {"name":{"text":"Faint signal"},"category":"story","min_tier":1,"color":"#7CFC00","reveal_distance":99,
                 "requires_stage":"era4","requires_unlock":true,"found_radius":10,"hide_when_found":true,
                 "locator":{"type":"structure","structure":"#minecraft:village","search_radius_chunks":50}}"""));
        h.assertTrue(o.isPresent(), "valid target rejected");
        TargetDef d = o.get();
        h.assertTrue(d.name().getString().equals("Faint signal"), "name " + d.name().getString());
        h.assertTrue(d.category().equals("story") && d.minTier() == 1 && d.color() == 0x7CFC00, "basic fields");
        h.assertTrue(d.revealDistance() == 99 && d.foundRadius() == 10 && d.hideWhenFound() && d.requiresUnlock(), "flag fields");
        h.assertTrue("era4".equals(d.requiresStage()), "stage " + d.requiresStage());
        h.assertTrue(d.locator() instanceof Locator.Structure s && s.tag() && s.searchRadiusChunks() == 50
                && s.id().equals(ResourceLocation.withDefaultNamespace("village")), "structure locator " + d.locator());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void targetIconParsedWithDefault(GameTestHelper h) {
        String loc = "\"locator\":{\"type\":\"pos\",\"pos\":[0,0,0]}";
        h.assertTrue(TargetParser.parse(ID, json("{" + loc + "}")).orElseThrow().icon().equals("item:minecraft:compass"), "default icon");
        h.assertTrue(TargetParser.parse(ID, json("{\"icon\":\"minecraft:map\"," + loc + "}")).orElseThrow().icon().equals("item:minecraft:map"),
                "bare id is an item");
        h.assertTrue(TargetParser.parse(ID, json("{\"icon\":\"block:minecraft:gold_ore\"," + loc + "}")).orElseThrow().icon()
                .equals("block:minecraft:gold_ore"), "block icon");
        h.assertTrue(TargetParser.parse(ID, json("{\"icon\":\"entity:minecraft:zombie\"," + loc + "}")).orElseThrow().icon()
                .equals("entity:minecraft:zombie"), "entity icon");
        h.assertTrue(TargetParser.parse(ID, json("{\"icon\":\"nonsense:::\"," + loc + "}")).isEmpty(), "bad icon accepted");
        // the icon travels with the blip
        TargetDef d = TargetParser.parse(ID, json("{\"icon\":\"minecraft:clock\"," + loc + "}")).orElseThrow();
        h.assertTrue(d.icon().equals("item:minecraft:clock"), "parsed icon");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void targetParsesAllLocatorsAndDefaults(GameTestHelper h) {
        TargetDef pos = TargetParser.parse(ID, json("{\"locator\":{\"type\":\"pos\",\"pos\":[1,2,3],\"dimension\":\"minecraft:the_nether\"}}")).orElseThrow();
        h.assertTrue(pos.locator() instanceof Locator.Pos p && p.pos().equals(new BlockPos(1, 2, 3))
                && p.dimension().equals(ResourceLocation.withDefaultNamespace("the_nether")), "pos " + pos.locator());
        h.assertTrue(pos.revealDistance() == 128 && pos.minTier() == 0 && pos.category().equals("narrative")
                && pos.foundRadius() == 24 && !pos.hideWhenFound() && pos.requiresStage() == null, "defaults");
        TargetDef ent = TargetParser.parse(ID, json("{\"locator\":{\"type\":\"entity\",\"entity_type\":\"minecraft:pig\",\"tag\":\"sr\"}}")).orElseThrow();
        h.assertTrue(ent.locator() instanceof Locator.Entity e && "sr".equals(e.tag()) && e.entityType() != null, "entity " + ent.locator());
        TargetDef blk = TargetParser.parse(ID, json("{\"locator\":{\"type\":\"block\",\"block\":\"#c:ores\",\"radius\":9}}")).orElseThrow();
        h.assertTrue(blk.locator() instanceof Locator.Block b && b.tag() && b.radius() == 9, "block " + blk.locator());
        TargetDef big = TargetParser.parse(ID, json("{\"locator\":{\"type\":\"block\",\"block\":\"stone\",\"radius\":500}}")).orElseThrow();
        h.assertTrue(big.locator() instanceof Locator.Block b2 && b2.radius() == Locator.Block.MAX_RADIUS, "radius not clamped " + big.locator());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void invalidTargetsAreSkipped(GameTestHelper h) {
        String[] bad = {
                "[]",
                "{}",
                "{\"locator\":{\"type\":\"warp\"}}",
                "{\"min_tier\":9,\"locator\":{\"type\":\"pos\",\"pos\":[0,0,0]}}",
                "{\"color\":\"red\",\"locator\":{\"type\":\"pos\",\"pos\":[0,0,0]}}",
                "{\"locator\":{\"type\":\"pos\",\"pos\":[0,0]}}",
                "{\"locator\":{\"type\":\"entity\"}}",
                "{\"locator\":{\"type\":\"structure\",\"structure\":\"Not Valid!\"}}",
        };
        for (String b : bad) {
            h.assertTrue(TargetParser.parse(ID, json(b)).isEmpty(), "accepted bad target: " + b);
        }
        Map<ResourceLocation, JsonElement> raw = new java.util.LinkedHashMap<>();
        raw.put(SignalRadar.id("bad"), json("{\"locator\":{\"type\":\"warp\"}}"));
        raw.put(SignalRadar.id("good"), json("{\"locator\":{\"type\":\"pos\",\"pos\":[0,0,0]}}"));
        Map<ResourceLocation, TargetDef> parsed = TargetManager.parseAll(raw);
        h.assertTrue(parsed.size() == 1 && parsed.containsKey(SignalRadar.id("good")), "parseAll " + parsed.keySet());
        h.succeed();
    }

    // ------------------------------------------------------------------ scan rules

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void tierGatesTargets(GameTestHelper h) {
        List<TargetDef> defs = List.of(def("a", 0, 128), def("b", 2, 128), def("c", 4, 128));
        Map<String, Double> xs = Map.of("a", 10.0, "b", 10.0, "c", 10.0);
        ScanSnapshot t1 = scan(radar(1, 1000), 0, defs, xs);
        h.assertTrue(t1.blips().size() == 1 && t1.blips().get(0).id().equals("signalradar:a"), "tier 1 blips " + t1.blips().size());
        ScanSnapshot t2 = scan(radar(2, 1000), 0, defs, xs);
        h.assertTrue(t2.blips().size() == 2, "tier 2 blips " + t2.blips().size());
        ScanSnapshot t4 = scan(radar(4, 1000), 0, defs, xs);
        h.assertTrue(t4.blips().size() == 3, "tier 4 blips " + t4.blips().size());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void unlocatedTargetsAreSkipped(GameTestHelper h) {
        ScanSnapshot s = scan(radar(0, 1000), 0, List.of(def("a", 0, 128), def("b", 0, 128)), Map.of("a", 10.0));
        h.assertTrue(s.blips().size() == 1, "blips " + s.blips().size());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void rangeFlagsOutOfRangeTargets(GameTestHelper h) {
        List<TargetDef> defs = List.of(def("far", 0, 128));
        Map<String, Double> xs = Map.of("far", 300.0);
        Blip t0 = scan(radar(0, 1000), 0, defs, xs).blips().get(0); // range 256
        h.assertTrue(t0.outOfRange(), "300 m at tier 0 should be out of range");
        Blip t1 = scan(radar(1, 1000), 0, defs, xs).blips().get(0); // range 512
        h.assertTrue(!t1.outOfRange(), "300 m at tier 1 should be in range");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void fuzzStaysWithinBounds(GameTestHelper h) {
        // tier 0: max fuzz 64, range 256. At 128 m -> magnitude 32; beyond range -> 64.
        for (int i = 0; i < 200; i++) {
            UUID p = new UUID(i, 7);
            for (double dist : new double[] {0, 10, 128, 256, 1000}) {
                double mag = ScanMath.fuzzMagnitude(64, dist, 256);
                double expected = 64 * Math.min(1.0, dist / 256.0);
                h.assertTrue(Math.abs(mag - expected) < 1e-9, "magnitude " + mag + " vs " + expected);
                Vec3 real = new Vec3(dist, 70, 5);
                Vec3 f = ScanMath.fuzz(p, "signalradar:a", i * 200L, real, mag);
                double off = Math.hypot(f.x - real.x, f.z - real.z);
                h.assertTrue(off <= mag + 1e-9, "offset " + off + " > " + mag);
                h.assertTrue(f.y == real.y, "y changed");
            }
        }
        // Near targets are sharp: distance 0 -> no fuzz at all.
        Vec3 p = new Vec3(3, 64, 3);
        h.assertTrue(ScanMath.fuzz(PLAYER, "x", 0, p, ScanMath.fuzzMagnitude(64, 0, 256)).equals(p), "fuzz at distance 0");
        // End to end through the scanner: blip within magnitude of the real position.
        Blip b = scan(radar(0, 1000), 4000, List.of(def("a", 0, 128)), Map.of("a", 128.0)).blips().get(0);
        h.assertTrue(Math.hypot(b.x() - 128.0, b.z()) <= 32 + 1e-9, "scanner fuzz beyond 32: " + b.x() + "," + b.z());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void fuzzIsDeterministicWithinBucket(GameTestHelper h) {
        Vec3 real = new Vec3(100, 64, 100);
        Vec3 a = ScanMath.fuzz(PLAYER, "signalradar:a", 1000, real, 32);
        Vec3 b = ScanMath.fuzz(PLAYER, "signalradar:a", 1199, real, 32); // same 10 s bucket (1000..1199)
        h.assertTrue(a.equals(b), "fuzz changed inside a bucket");
        boolean changedAcrossBuckets = false;
        for (int k = 1; k <= 5; k++) {
            if (!ScanMath.fuzz(PLAYER, "signalradar:a", 1000 + 200L * k, real, 32).equals(a)) {
                changedAcrossBuckets = true;
            }
        }
        h.assertTrue(changedAcrossBuckets, "fuzz never changes across buckets");
        h.assertTrue(!ScanMath.fuzz(PLAYER, "signalradar:b", 1000, real, 32).equals(a), "different targets share fuzz");
        h.assertTrue(!ScanMath.fuzz(new UUID(1, 2), "signalradar:a", 1000, real, 32).equals(a), "different players share fuzz");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void tierFourHasNoFuzz(GameTestHelper h) {
        for (long t = 0; t < 2000; t += 200) {
            Blip b = scan(radar(4, 1000), t, List.of(def("a", 0, 128)), Map.of("a", 3000.0)).blips().get(0);
            h.assertTrue(b.x() == 3000.0 && b.y() == 64 && b.z() == 0.0, "tier 4 fuzzed: " + b.x() + "," + b.z());
        }
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void nameIsRevealedByDistance(GameTestHelper h) {
        List<TargetDef> defs = List.of(def("near", 0, 128), def("far", 0, 128), def("edge", 0, 128));
        ScanSnapshot s = scan(radar(1, 1000), 0, defs, Map.of("near", 100.0, "far", 200.0, "edge", 128.0));
        Map<String, Blip> by = new java.util.HashMap<>();
        s.blips().forEach(b -> by.put(b.id(), b));
        h.assertTrue(by.get("signalradar:near").name().getString().equals("Name near"), "near name hidden");
        h.assertTrue(by.get("signalradar:edge").name().getString().equals("Name edge"), "edge (== reveal distance) name hidden");
        h.assertTrue(by.get("signalradar:far").name().getString().equals("???"), "far name shown");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void scanChargesEnergy(GameTestHelper h) {
        ItemStack r = radar(0, 1000);
        ScanSnapshot s = scan(r, 0, List.of(def("a", 0, 128)), Map.of("a", 10.0));
        h.assertTrue(!s.noSignal(), "unexpected NO SIGNAL");
        h.assertTrue(RadarItem.energy(r) == 950 && s.energy() == 950, "energy " + RadarItem.energy(r) + " / " + s.energy());
        h.assertTrue(s.tier() == 0 && s.capacity() == SignalRadarConfig.capacity(), "snapshot header");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void emptyRadarHasNoSignal(GameTestHelper h) {
        ItemStack r = radar(2, 49);
        ScanSnapshot s = scan(r, 0, List.of(def("a", 0, 128)), Map.of("a", 10.0));
        h.assertTrue(s.noSignal() && s.blips().isEmpty(), "expected NO SIGNAL without blips");
        h.assertTrue(RadarItem.energy(r) == 49, "energy charged on a failed scan: " + RadarItem.energy(r));
        ItemStack exact = radar(0, 50);
        h.assertTrue(!scan(exact, 0, List.of(), Map.of()).noSignal() && RadarItem.energy(exact) == 0, "exactly scanCost FE must pay");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void configTierListsFallBackToDefaults(GameTestHelper h) {
        int[] r = SignalRadarConfig.sanitizeTierList(List.of(1, 2, 3), SignalRadarConfig.DEFAULT_RANGE, "range");
        h.assertTrue(r.length == 5 && r[0] == 256 && r[4] == 4096, "short list not replaced");
        int[] ok = SignalRadarConfig.sanitizeTierList(List.of(1, 2, 3, 4, 5), SignalRadarConfig.DEFAULT_RANGE, "range");
        h.assertTrue(ok[0] == 1 && ok[4] == 5, "valid list altered");
        h.succeed();
    }

    // ------------------------------------------------------------------ structure queue

    private static final Locator.Structure VILLAGE = new Locator.Structure(ResourceLocation.withDefaultNamespace("village_plains"), false, 10);

    private static Locator.Structure structure(int i) {
        return new Locator.Structure(ResourceLocation.fromNamespaceAndPath("test", "s" + i), false, 10);
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureQueueRespectsPerTickLimit(GameTestHelper h) {
        AtomicInteger calls = new AtomicInteger();
        StructureLookupService svc = new StructureLookupService((lvl, loc, origin) -> {
            calls.incrementAndGet();
            return new BlockPos(100, 64, 100);
        });
        StructureCacheData data = new StructureCacheData();
        var dim = h.getLevel().dimension();
        for (int i = 0; i < 5; i++) {
            h.assertTrue(svc.get(data, dim, structure(i), BlockPos.ZERO, 0).isEmpty(), "pending must not show");
        }
        // Requesting the same key again must not queue a duplicate.
        svc.get(data, dim, structure(0), BlockPos.ZERO, 0);
        h.assertTrue(svc.pending() == 5, "pending " + svc.pending());
        int ran = svc.tick(k -> h.getLevel(), data, 1, 1);
        h.assertTrue(ran == 1 && calls.get() == 1 && svc.pending() == 4, "tick 1: ran " + ran + " calls " + calls + " pending " + svc.pending());
        ran = svc.tick(k -> h.getLevel(), data, 2, 2);
        h.assertTrue(ran == 2 && calls.get() == 3 && svc.pending() == 2, "tick 2: ran " + ran + " calls " + calls);
        svc.tick(k -> h.getLevel(), data, 3, 10);
        h.assertTrue(svc.pending() == 0 && calls.get() == 5, "drain: calls " + calls);
        h.assertTrue(svc.get(data, dim, structure(3), BlockPos.ZERO, 4).equals(Optional.of(new BlockPos(100, 64, 100))), "hit not returned");
        h.assertTrue(svc.pending() == 0 && calls.get() == 5, "a cached hit must never search again");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureMissesRetryEveryFiveMinutes(GameTestHelper h) {
        AtomicInteger calls = new AtomicInteger();
        StructureLookupService svc = new StructureLookupService((lvl, loc, origin) -> {
            calls.incrementAndGet();
            return null;
        });
        StructureCacheData data = new StructureCacheData();
        var dim = h.getLevel().dimension();
        long t0 = 1000;
        svc.get(data, dim, VILLAGE, BlockPos.ZERO, t0);
        svc.tick(k -> h.getLevel(), data, t0, 1);
        String key = StructureCacheData.key(dim, VILLAGE, BlockPos.ZERO, SignalRadarConfig.structureCellSize());
        h.assertTrue(calls.get() == 1 && data.entry(key) != null && data.entry(key).pos() == null, "miss not cached");
        // Before 5 minutes: asking again never queues.
        svc.get(data, dim, VILLAGE, BlockPos.ZERO, t0 + StructureLookupService.MISS_RETRY_TICKS - 1);
        h.assertTrue(svc.pending() == 0, "retried too early");
        // At 5 minutes: queued exactly once.
        h.assertTrue(svc.get(data, dim, VILLAGE, BlockPos.ZERO, t0 + StructureLookupService.MISS_RETRY_TICKS).isEmpty(), "miss returned a position");
        svc.get(data, dim, VILLAGE, BlockPos.ZERO, t0 + StructureLookupService.MISS_RETRY_TICKS + 1);
        h.assertTrue(svc.pending() == 1, "expected exactly one retry queued, pending " + svc.pending());
        svc.tick(k -> h.getLevel(), data, t0 + StructureLookupService.MISS_RETRY_TICKS, 1);
        h.assertTrue(calls.get() == 2 && data.entry(key).time() == t0 + StructureLookupService.MISS_RETRY_TICKS, "retry did not run");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureCacheSurvivesSaveAndClear(GameTestHelper h) {
        StructureCacheData data = new StructureCacheData();
        data.putFound("a@0,0", new BlockPos(1, 2, 3), 5);
        data.putMiss("b@1,-1", 9);
        data.putFound("legacy", new BlockPos(7, 7, 7), 1); // saves before the region cache had no cell
        StructureCacheData copy = StructureCacheData.load(data.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(new BlockPos(1, 2, 3).equals(copy.entry("a@0,0").pos()) && copy.entry("a@0,0").time() == 5, "hit lost");
        h.assertTrue(copy.entry("b@1,-1").pos() == null && copy.entry("b@1,-1").time() == 9, "miss lost");
        h.assertTrue(copy.entry("legacy") == null, "a cell-less legacy entry was loaded");
        copy.clear();
        h.assertTrue(copy.entries().isEmpty(), "clear failed");
        h.succeed();
    }

    // ------------------------------------------------------------------ structure region cells

    private static final int CELL = 256;

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureNewCellQueuesAndReplacesResults(GameTestHelper h) {
        AtomicInteger calls = new AtomicInteger();
        // the fake finder answers "10 blocks south-east of where you searched from"
        StructureLookupService svc = new StructureLookupService((lvl, loc, origin) -> {
            calls.incrementAndGet();
            return origin.offset(10, 0, 10);
        });
        StructureCacheData data = new StructureCacheData();
        var dim = h.getLevel().dimension();
        BlockPos a = new BlockPos(5, 64, 5);
        h.assertTrue(svc.get(data, dim, VILLAGE, a, 0, CELL).isEmpty() && svc.pending() == 1, "first cell not queued");
        svc.tick(k -> h.getLevel(), data, 1, 1);
        h.assertTrue(svc.get(data, dim, VILLAGE, a, 2, CELL).equals(Optional.of(new BlockPos(15, 64, 15))), "first cell hit");
        h.assertTrue(svc.pending() == 0, "a cell with a hit queued again");
        // the player walks into the next cell: the old result keeps showing, a new search is queued
        BlockPos b = new BlockPos(300, 64, 5);
        h.assertTrue(svc.get(data, dim, VILLAGE, b, 3, CELL).equals(Optional.of(new BlockPos(15, 64, 15))), "previous cell result not kept");
        h.assertTrue(svc.pending() == 1, "entering a new cell did not queue a search, pending " + svc.pending());
        svc.tick(k -> h.getLevel(), data, 4, 1);
        h.assertTrue(calls.get() == 2, "calls " + calls);
        h.assertTrue(svc.get(data, dim, VILLAGE, b, 5, CELL).equals(Optional.of(new BlockPos(310, 64, 15))), "new cell did not replace the result");
        // back in the first cell its own (nearer) hit wins again, without searching
        h.assertTrue(svc.get(data, dim, VILLAGE, a, 6, CELL).equals(Optional.of(new BlockPos(15, 64, 15))), "nearest of the neighbourhood");
        h.assertTrue(svc.pending() == 0 && calls.get() == 2, "cached cells searched again");
        // far away (not next to any cached cell): nothing shown until the search runs
        BlockPos far = new BlockPos(5000, 64, -5000);
        h.assertTrue(svc.get(data, dim, VILLAGE, far, 7, CELL).isEmpty() && svc.pending() == 1, "far cell");
        h.assertTrue(svc.peek(data, dim, VILLAGE, b).equals(Optional.of(new BlockPos(310, 64, 15))), "peek around b");
        h.assertTrue(svc.peek(data, dim, VILLAGE, far).isEmpty() && svc.pending() == 1, "peek must not queue");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureMissesRetryPerCell(GameTestHelper h) {
        AtomicInteger calls = new AtomicInteger();
        StructureLookupService svc = new StructureLookupService((lvl, loc, origin) -> {
            calls.incrementAndGet();
            return null;
        });
        StructureCacheData data = new StructureCacheData();
        var dim = h.getLevel().dimension();
        BlockPos a = BlockPos.ZERO;
        BlockPos b = new BlockPos(-10, 64, 0); // cell (-1, 0)
        svc.get(data, dim, VILLAGE, a, 0, CELL);
        svc.tick(k -> h.getLevel(), data, 0, 1);
        svc.get(data, dim, VILLAGE, a, 10, CELL);
        h.assertTrue(svc.pending() == 0, "the missed cell retried too early");
        svc.get(data, dim, VILLAGE, b, 10, CELL);
        h.assertTrue(svc.pending() == 1, "a miss in one cell blocked the search of another cell");
        svc.tick(k -> h.getLevel(), data, 10, 1);
        h.assertTrue(calls.get() == 2, "calls " + calls);
        svc.get(data, dim, VILLAGE, a, StructureLookupService.MISS_RETRY_TICKS, CELL);
        svc.get(data, dim, VILLAGE, b, StructureLookupService.MISS_RETRY_TICKS, CELL);
        h.assertTrue(svc.pending() == 1, "only cell a is due after 5 minutes, pending " + svc.pending());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureCacheIsBoundedLru(GameTestHelper h) {
        StructureCacheData data = new StructureCacheData(3);
        data.putFound("s@0,0", new BlockPos(1, 0, 1), 0);
        data.putFound("s@1,0", new BlockPos(2, 0, 2), 0);
        data.putMiss("s@2,0", 0);
        data.entry("s@0,0"); // used recently: survives
        data.putFound("s@3,0", new BlockPos(4, 0, 4), 0);
        h.assertTrue(data.entries().size() == 3, "size " + data.entries().size());
        h.assertTrue(data.entry("s@1,0") == null && data.entry("s@0,0") != null && data.entry("s@3,0") != null, "wrong entry evicted");
        h.assertTrue(new StructureCacheData().maxEntries() == StructureCacheData.MAX_ENTRIES, "default bound");
        // filling the real-size cache never grows past the bound
        StructureCacheData big = new StructureCacheData();
        for (int i = 0; i < StructureCacheData.MAX_ENTRIES + 100; i++) {
            big.putMiss("x@" + i + ",0", i);
        }
        h.assertTrue(big.entries().size() == StructureCacheData.MAX_ENTRIES && big.entry("x@0,0") == null, "bound exceeded");
        h.succeed();
    }

    // ------------------------------------------------------------------ locators

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void blockLocatorFindsNearestLoadedBlock(GameTestHelper h) {
        BlockPos near = h.absolutePos(new BlockPos(0, 2, 0));
        h.setBlock(new BlockPos(0, 2, 0), Blocks.DIAMOND_BLOCK);
        Vec3 from = Vec3.atCenterOf(near).add(4, 0, 0);
        var match = BlockLocatorScan.matcher(new Locator.Block(ResourceLocation.withDefaultNamespace("diamond_block"), false, 8)).orElseThrow();
        Optional<BlockPos> hit = BlockLocatorScan.find(h.getLevel(), from, 8, match);
        h.assertTrue(hit.isPresent() && hit.get().equals(near), "found " + hit);
        h.assertTrue(BlockLocatorScan.find(h.getLevel(), from.add(20, 0, 0), 8, match).isEmpty(), "found a block outside the radius");
        var none = BlockLocatorScan.matcher(new Locator.Block(ResourceLocation.withDefaultNamespace("emerald_block"), false, 8)).orElseThrow();
        h.assertTrue(BlockLocatorScan.find(h.getLevel(), from, 8, none).isEmpty(), "found an absent block");
        h.assertTrue(BlockLocatorScan.matcher(new Locator.Block(ResourceLocation.parse("nope:nothing"), false, 8)).isEmpty(), "unknown block accepted");
        // Work budget: nothing left = no search; one section's worth is taken per searched section.
        h.assertTrue(BlockLocatorScan.find(h.getLevel(), from, 8, match, new BlockLocatorScan.Budget(0)).isEmpty(), "searched without budget");
        BlockLocatorScan.Budget budget = new BlockLocatorScan.Budget(1_000_000);
        h.assertTrue(BlockLocatorScan.find(h.getLevel(), from, 8, match, budget).isPresent(), "budgeted search failed");
        h.assertTrue(budget.remaining() < 1_000_000 && (1_000_000 - budget.remaining()) % 4096 == 0, "budget " + budget.remaining());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void blockLocatorCacheHonoursTtl(GameTestHelper h) {
        BlockLocatorScan cache = new BlockLocatorScan();
        AtomicInteger runs = new AtomicInteger();
        java.util.function.Supplier<Optional<BlockPos>> compute = () -> {
            runs.incrementAndGet();
            return Optional.empty();
        };
        cache.cached(PLAYER, "k", 100, 100, compute);
        cache.cached(PLAYER, "k", 199, 100, compute);
        h.assertTrue(runs.get() == 1, "recomputed inside ttl");
        cache.cached(PLAYER, "k", 200, 100, compute);
        h.assertTrue(runs.get() == 2, "not recomputed after ttl");
        cache.cached(new UUID(9, 9), "k", 200, 100, compute);
        h.assertTrue(runs.get() == 3, "cache shared between players");
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void entityAndPosLocators(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setPos(h.absolutePos(new BlockPos(0, 2, 0)).getCenter());
        Entity stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 1));
        stand.addTag("sr_test");
        Optional<Vec3> found = Locators.locate(p, new Locator.Entity(null, "sr_test"), 64, h.getLevel(), 0);
        h.assertTrue(found.isPresent() && found.get().distanceTo(stand.position()) < 1e-6, "tagged entity not found: " + found);
        h.assertTrue(Locators.locate(p, new Locator.Entity(ResourceLocation.withDefaultNamespace("armor_stand"), "other"), 64, h.getLevel(), 0).isEmpty(),
                "wrong tag matched");
        h.assertTrue(Locators.locate(p, new Locator.Entity(ResourceLocation.withDefaultNamespace("pig"), "sr_test"), 64, h.getLevel(), 0).isEmpty(),
                "wrong type matched");
        h.assertTrue(Locators.locate(p, new Locator.Pos(new BlockPos(5, 6, 7), ResourceLocation.withDefaultNamespace("overworld")), 64, h.getLevel(), 0)
                .isPresent(), "pos locator in current dimension");
        h.assertTrue(Locators.locate(p, new Locator.Pos(new BlockPos(5, 6, 7), ResourceLocation.withDefaultNamespace("the_nether")), 64, h.getLevel(), 0)
                .isEmpty(), "pos locator in other dimension must be hidden");
        h.succeed();
    }

    // ------------------------------------------------------------------ payload, handler, commands

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void snapshotPayloadRoundTrips(GameTestHelper h) {
        List<Blip> blips = new ArrayList<>();
        blips.add(new Blip("signalradar:a", "narrative", 0x7CFC00, 1.5, 64.0, -3000.25, Component.literal("Faint signal"), false, false, "item:minecraft:compass"));
        blips.add(new Blip("signalradar:b", "story", 0x123456, -1, 2, 3, Blip.UNKNOWN_NAME, true, true, "block:minecraft:iron_ore"));
        ScanSnapshot in = new ScanSnapshot(3, 12345, 20000, 2048, 5, false, 987654321L, blips, true, 48);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        SnapshotPayload.CODEC.encode(buf, new SnapshotPayload(in));
        ScanSnapshot out = SnapshotPayload.CODEC.decode(buf).snapshot();
        h.assertTrue(out.tier() == 3 && out.energy() == 12345 && out.capacity() == 20000 && out.range() == 2048
                && out.refreshSeconds() == 5 && !out.noSignal() && out.gameTime() == 987654321L && out.motionRadius() == 48 && out.charged(), "header");
        h.assertTrue(out.blips().size() == 2, "blip count");
        Blip a = out.blips().get(0);
        Blip b = out.blips().get(1);
        h.assertTrue(a.id().equals("signalradar:a") && a.color() == 0x7CFC00 && a.x() == 1.5 && a.z() == -3000.25
                && a.name().getString().equals("Faint signal") && !a.outOfRange() && !a.found() && a.icon().equals("item:minecraft:compass"), "blip a");
        h.assertTrue(b.outOfRange() && b.found() && b.name().getString().equals("???") && b.category().equals("story") && b.icon().equals("block:minecraft:iron_ore"), "blip b");
        h.assertTrue(buf.readableBytes() == 0, "trailing bytes");
        // A blip count above the cap is rejected, not truncated.
        RegistryFriendlyByteBuf bad = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        bad.writeVarInt(0).writeVarInt(0).writeVarInt(0).writeVarInt(0).writeVarInt(0);
        bad.writeBoolean(false).writeBoolean(false).writeLong(0L).writeVarInt(0).writeVarInt(1_000_000);
        boolean threw = false;
        try {
            SnapshotPayload.CODEC.decode(bad);
        } catch (RuntimeException e) {
            threw = true;
        }
        h.assertTrue(threw, "oversized blip count accepted");
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void heldRadarIsFoundInEitherHand(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        h.assertTrue(ScanHandler.heldRadar(p).isEmpty(), "empty hands");
        ItemStack r = radar(0, 100);
        p.setItemInHand(InteractionHand.OFF_HAND, r);
        h.assertTrue(ScanHandler.heldRadar(p) == r, "offhand radar not found");
        ItemStack main = radar(1, 100);
        p.setItemInHand(InteractionHand.MAIN_HAND, main);
        h.assertTrue(ScanHandler.heldRadar(p) == main, "main hand should win");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    /** Runs one scan tick of the player; returns the radar's energy afterwards. */
    private static int tickEnergy(ServerPlayer p, ItemStack radar) {
        ScanHandler.reset();
        try {
            ScanHandler.tickPlayer(p);
        } catch (RuntimeException e) {
            // The mock connection refuses the snapshot payload; the paid scan already drained the energy by then.
        }
        return RadarItem.energy(radar);
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void twoHandedMainHandBlocksOffhandRadar(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ItemStack r = radar(0, 500);
        p.setItemInHand(InteractionHand.OFF_HAND, r);
        // Normal item in the main hand: the offhand radar scans and pays.
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(!TwoHanded.blocksOffhand(p) && ScanHandler.activeRadar(p) == r, "stick blocked the offhand radar");
        h.assertTrue(tickEnergy(p, r) < 500, "offhand radar with a stick did not pay");
        // Charged crossbow in the main hand: off, no scan, no drain.
        RadarItem.setEnergy(r, 500);
        ItemStack bow = new ItemStack(Items.CROSSBOW);
        p.setItemInHand(InteractionHand.MAIN_HAND, bow);
        h.assertTrue(!TwoHanded.blocksOffhand(p), "empty crossbow blocks");
        bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        h.assertTrue(TwoHanded.blocksOffhand(p) && ScanHandler.activeRadar(p).isEmpty(), "charged crossbow does not block");
        h.assertTrue(tickEnergy(p, r) == 500, "blocked offhand radar drained energy: " + RadarItem.energy(r));
        // Back to a normal item: scans again at once (no extra schedule reset needed).
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(tickEnergy(p, r) < 500, "offhand radar did not resume");
        // The main-hand radar is never affected.
        RadarItem.setEnergy(r, 500);
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        h.assertTrue(!TwoHanded.blocksOffhand(p) && ScanHandler.activeRadar(p) == r, "main-hand radar blocked");
        h.assertTrue(tickEnergy(p, r) < 500, "main-hand radar did not pay");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ScanHandler.reset();
        h.succeed();
    }

    @SuppressWarnings({"removal", "unchecked"})
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void twoHandedTagBlocksOffhandRadar(GameTestHelper h) throws Exception {
        ServerPlayer p = TestPlayers.create(h);
        ItemStack r = radar(0, 500);
        p.setItemInHand(InteractionHand.OFF_HAND, r);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        h.assertTrue(!TwoHanded.blocksOffhand(p), "untagged stick blocks");
        // Bind the tag to the stick for this test (the shipped tag is empty).
        var holder = Items.STICK.builtInRegistryHolder();
        var bind = net.minecraft.core.Holder.Reference.class.getDeclaredMethod("bindTags", java.util.Collection.class);
        bind.setAccessible(true);
        var old = java.util.Set.copyOf(holder.tags().toList());
        bind.invoke(holder, java.util.Set.of(TwoHanded.TAG));
        try {
            h.assertTrue(TwoHanded.blocksOffhand(p), "tagged item does not block");
            h.assertTrue(tickEnergy(p, r) == 500, "tagged main hand item: offhand radar drained");
        } finally {
            bind.invoke(holder, old);
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            ScanHandler.reset();
        }
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void commandsWork(GameTestHelper h) throws Exception {
        ServerPlayer p = TestPlayers.create(h);
        ItemStack r = radar(0, 10);
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        var src = p.createCommandSourceStack().withPermission(2);
        dispatcher.execute("signalradar settier @s 3", src);
        h.assertTrue(RadarItem.tier(r) == 3, "settier: " + RadarItem.tier(r));
        dispatcher.execute("signalradar charge @s", src);
        h.assertTrue(RadarItem.energy(r) == SignalRadarConfig.capacity(), "charge: " + RadarItem.energy(r));
        dispatcher.execute("signalradar targets", src);
        dispatcher.execute("signalradar clearcache", src);
        boolean rejected = false;
        try {
            dispatcher.execute("signalradar settier @s 9", src);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            rejected = true;
        }
        h.assertTrue(rejected && RadarItem.tier(r) == 3, "tier 9 accepted");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        boolean noRadar = false;
        try {
            dispatcher.execute("signalradar settier @s 1", src);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            noRadar = true;
        }
        h.assertTrue(noRadar, "settier without radar should fail");
        // Permission: level 0 source must not even see the command.
        boolean denied = false;
        try {
            dispatcher.execute("signalradar clearcache", p.createCommandSourceStack().withPermission(0));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            denied = true;
        }
        h.assertTrue(denied, "non-op could run /signalradar");
        h.succeed();
    }
}
