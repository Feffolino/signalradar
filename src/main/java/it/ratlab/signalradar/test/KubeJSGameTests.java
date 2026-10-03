// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import static it.ratlab.signalradar.test.EventGameTests.HIDDEN;
import static it.ratlab.signalradar.test.EventGameTests.LOCKED;
import static it.ratlab.signalradar.test.EventGameTests.VISIBLE;
import static it.ratlab.signalradar.test.EventGameTests.blip;
import static it.ratlab.signalradar.test.EventGameTests.command;
import static it.ratlab.signalradar.test.EventGameTests.grid;
import static it.ratlab.signalradar.test.EventGameTests.player;
import static it.ratlab.signalradar.test.EventGameTests.radar;
import static it.ratlab.signalradar.test.EventGameTests.scan;
import static it.ratlab.signalradar.test.EventGameTests.withTargets;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonItem;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.api.SignalRadarAPI;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.progress.FoundHandler;
import it.ratlab.signalradar.progress.StageHelper;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.scan.ScanSnapshot;
import it.ratlab.signalradar.target.TargetManager;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 7 KubeJS plugin, driven by the example scripts in {@code src/test_datapack/kubejs} (installed into
 * {@code run-kubejs/kubejs} by {@code tools/prepare-kubejs-run.sh}). Without KubeJS every test passes at once (plain
 * runs); {@code runGameTestServerKubeJS} exercises them. KubeJS classes are only touched in {@code test.kubejs}.
 */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class KubeJSGameTests {
    private static final String EMPTY = "gametest_empty";
    static final ResourceLocation OIL = new ResourceLocation("signalradar_example", "addon_oil");
    static final ResourceLocation UNDEAD = new ResourceLocation("signalradar_example", "addon_undead");
    static final ResourceLocation OPTIONAL = new ResourceLocation("signalradar_example", "addon_optional");

    private KubeJSGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(KubeJSGameTests.class));
    }

    private static boolean skip(GameTestHelper h, String test) {
        if (ModList.get().isLoaded("kubejs")) {
            SignalRadar.LOGGER.info("KubeJS check {} runs", test);
            return false;
        }
        h.succeed();
        return true;
    }

    private static Set<String> tags(ServerPlayer p) {
        return p.getTags();
    }

    private static String tagWith(ServerPlayer p, String prefix) {
        return tags(p).stream().filter(t -> t.startsWith(prefix)).findFirst().orElse(null);
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void kubejsCustomAddons(GameTestHelper h) {
        if (skip(h, "kubejsCustomAddons")) {
            return;
        }
        AddonDefinition oil = AddonRegistry.get(OIL).orElseThrow(() -> new IllegalStateException("oil addon missing"));
        h.assertTrue(oil.detector() == AddonDefinition.Detector.BLOCK_TAG && oil.minTier() == 3 && oil.radiusMin() == 16
                && oil.radiusMax() == 48 && oil.refreshSeconds() == 5 && oil.color() == 0x222222 && oil.energyCost() == 10
                && oil.category().equals("Oil") && oil.tag().equals(ResourceLocation.parse("c:ores/oil")) && oil.requiredModId() == null,
                "oil definition " + oil);
        h.assertTrue("item:minecraft:lava_bucket".equals(oil.icon()), "oil icon " + oil.icon());
        h.assertTrue(oil.texture().equals(ResourceLocation.parse("signalradar_example:item/addon_oil")), "oil default texture " + oil.texture());
        h.assertTrue(!AddonRegistry.isBuiltin(oil) && AddonRegistry.isActive(oil), "oil is an active custom addon");
        AddonItem oilItem = (AddonItem) AddonRegistry.item(OIL).orElseThrow(() -> new IllegalStateException("oil item missing"));
        ItemStack oilStack = new ItemStack(oilItem);
        h.assertTrue(oilItem.getName(oilStack).getString().equals("Radar Addon (Oil)"), "fallback name " + oilItem.getName(oilStack).getString());

        AddonDefinition undead = AddonRegistry.get(UNDEAD).orElseThrow();
        h.assertTrue(undead.detector() == AddonDefinition.Detector.ENTITY_TAG && undead.minTier() == 1 && undead.color() == 0x88AA55
                && undead.energyCost() == 15 && undead.refreshSeconds() == 2, "undead definition " + undead);
        h.assertTrue(undead.texture().equals(ResourceLocation.parse("signalradar_example:item/undead_skull")), "undead texture " + undead.texture());
        AddonDefinition plain = AddonRegistry.get(ResourceLocation.parse("signalradar_example:addon_plain")).orElseThrow();
        h.assertTrue(plain.texture().equals(ResourceLocation.parse("signalradar_example:item/addon_plain")), "plain texture " + plain.texture());
        AddonDefinition optional = AddonRegistry.get(OPTIONAL).orElseThrow();
        h.assertTrue(!AddonRegistry.isActive(optional) && AddonRegistry.item(OPTIONAL).isEmpty(), "optional addon has no item");

        // the custom entity_tag addon detects a zombie next to the player
        ServerPlayer p = player(h);
        h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 1, 0));
        var hits = CompatGameTests.run(UNDEAD, p, h, 4);
        h.assertTrue(hits.size() == 1, "undead hits " + hits);
        // installable at tier 1 through the normal rules, costs its energy
        ItemStack r = radar(1, 1000);
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        command(p, "signalradar addon @s add signalradar_example:addon_undead");
        h.assertTrue(SignalRadarAPI.hasAddon(r, UNDEAD), "installed by command");
        h.assertTrue(AddonSettings.of(undead).energyCost() == 15, "custom energy");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void kubejsScanEvent(GameTestHelper h) {
        if (skip(h, "kubejsScanEvent")) {
            return;
        }
        ServerPlayer p = player(h);
        withTargets(p, () -> {
            FoundHandler.recordDeath(p);
            ItemStack r = radar(2, 10000);
            ScanSnapshot plain = scan(p, r);
            h.assertTrue(blip(plain, HIDDEN.toString()) != null && blip(plain, RadarScanner.LAST_DEATH_ID) != null, "plain " + plain.blips());

            p.addTag("sr_example_edit");
            ScanSnapshot s = scan(p, r);
            p.removeTag("sr_example_edit");
            h.assertTrue(blip(s, HIDDEN.toString()) == null && blip(s, RadarScanner.LAST_DEATH_ID) == null
                    && blip(s, VISIBLE.toString()) != null, "script removed hidden + last death " + s.blips());
            Blip beacon = blip(s, "script/Script Beacon");
            h.assertTrue(beacon != null && beacon.color() == 0xFFAA00 && Math.abs(beacon.x() - (p.getX() + 10)) < 1e-6,
                    "script beacon " + beacon);
            String tag = tagWith(p, "sr_js_scan:");
            h.assertTrue(("sr_js_scan:2:" + VISIBLE + "=narrative,script/Script Beacon=script").equals(tag), "scan tag " + tag);

            p.addTag("sr_example_cancel");
            int before = RadarItem.energy(r);
            ScanSnapshot c = scan(p, r);
            p.removeTag("sr_example_cancel");
            h.assertTrue(c.noSignal() && c.blips().isEmpty() && RadarItem.energy(r) < before, "script cancel = NO SIGNAL " + c);
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void kubejsBinding(GameTestHelper h) {
        if (skip(h, "kubejsBinding")) {
            return;
        }
        ServerPlayer p = player(h);
        withTargets(p, () -> {
            ItemStack r = radar(2, 10000);
            RadarItem.setAddons(r, List.of(AddonRegistry.ORE));
            p.addTag("sr_example_binding");
            scan(p, r);
            p.removeTag("sr_example_binding");
            Vec3 hidden = SignalRadarAPI.getTargetPos(p.serverLevel(), HIDDEN);
            String expected = "sr_js_binding:true:2:1234:true:true:false:true:" + AddonRegistry.ORE + ":"
                    + HIDDEN + "+" + LOCKED + "+" + VISIBLE + ":" + Math.round(hidden.x) + "/" + Math.round(hidden.z);
            String tag = tagWith(p, "sr_js_binding:");
            h.assertTrue(expected.equals(tag), "binding tag " + tag + " expected " + expected);
            h.assertTrue(RadarItem.energy(r) == 1234 && RadarItem.tier(r) == 4, "setEnergy / setTier applied");
            h.assertTrue(!SignalRadarAPI.isUnlocked(p, LOCKED), "locked again");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void kubejsBindingBadIds(GameTestHelper h) {
        if (skip(h, "kubejsBindingBadIds")) {
            return;
        }
        var b = new it.ratlab.signalradar.compat.kubejs.SignalRadarBindingJS();
        var idOrNull = (java.util.function.Function<String, ResourceLocation>) it.ratlab.signalradar.compat.kubejs.SignalRadarBindingJS::idOrNull;
        h.assertTrue(idOrNull.apply(null) == null && idOrNull.apply("") == null && idOrNull.apply("  ") == null
                && idOrNull.apply("Bad Id!") == null && idOrNull.apply("a:B") == null, "bad ids give null");
        h.assertTrue(idOrNull.apply("foo").toString().equals("kubejs:foo") && idOrNull.apply("#c:ores/x").toString().equals("c:ores/x"), "good ids");
        ServerPlayer p = player(h);
        ItemStack r = radar(1, 500);
        ItemStack dirt = new ItemStack(net.minecraft.world.item.Items.DIRT);
        b.unlock(p, null);
        b.lock(p, "Bad Id!");
        b.resetFound(p, "");
        h.assertTrue(!b.isUnlocked(p, null) && !b.isFound(p, "Bad Id!") && b.getTargetPos(h.getLevel(), null) == null, "bad target ids are harmless");
        h.assertTrue(!b.hasAddon(r, null) && !b.hasAddon(r, "x y") && !b.hasAddon(null, "signalradar:addon_ore"), "hasAddon bad input");
        b.setTier(dirt, 3);
        b.setEnergy(dirt, 100);
        b.setTier(null, 3);
        h.assertTrue(!b.isRadar(dirt) && !b.isRadar(null) && !b.isRadar(ItemStack.EMPTY) && b.getTier(dirt) == 0 && b.getEnergy(dirt) == 0
                && b.getAddons(dirt).isEmpty() && b.getAddons(null).isEmpty() && b.getTier(null) == 0, "non-radar stacks");
        b.setTier(r, 2);
        h.assertTrue(b.getTier(r) == 2 && b.isRadar(r), "valid radar still works");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void kubejsFoundUpgradeAddonEvents(GameTestHelper h) {
        if (skip(h, "kubejsFoundUpgradeAddonEvents")) {
            return;
        }
        ServerPlayer p = player(h);
        withTargets(p, () -> {
            // found: event + KubeJS stage (the phase 5 stage backend uses KubeJS stages when KubeJS is loaded)
            FoundHandler.found(p, TargetManager.get(HIDDEN));
            String stage = "signalradar_found_" + HIDDEN.getPath();
            h.assertTrue(StageHelper.has(p, stage) && it.ratlab.signalradar.test.kubejs.KubeJSChecks.hasStage(p, stage),
                    "KubeJS stage " + stage);
            h.assertTrue(tags(p).contains("sr_js_found:" + HIDDEN + ":Name " + HIDDEN.getPath()), "found tag " + tags(p));
            h.assertTrue(tags(p).contains("sr_js_found_stage:" + HIDDEN.getPath()), "script saw the stage " + tags(p));
            h.assertTrue(SignalRadarAPI.isFound(p, HIDDEN), "found");
            // resetFound from the script (binding) when the player has the binding tag
            p.addTag("sr_example_binding");
            FoundHandler.found(p, TargetManager.get(VISIBLE));
            p.removeTag("sr_example_binding");
            h.assertTrue(!SignalRadarAPI.isFound(p, VISIBLE), "script resetFound");
        });

        ItemStack in = radar(1, 500);
        ItemStack out = in.copy();
        RadarItem.setTier(out, 2);
        ForgeEventFactory.firePlayerCraftingEvent(p, out, grid(in, new ItemStack(ModItems.MODULE_2.get())));
        h.assertTrue(tags(p).contains("sr_js_upgraded:1>2:2"), "upgraded tag " + tags(p));

        ItemStack r = radar(2, 1000);
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        command(p, "signalradar addon @s add signalradar:addon_ore");
        command(p, "signalradar addon @s remove signalradar:addon_ore");
        h.assertTrue(tags(p).contains("sr_js_addon:0:none>signalradar:addon_ore")
                && tags(p).contains("sr_js_addon:0:signalradar:addon_ore>none"), "addon tags " + tags(p));
        h.succeed();
    }
}
