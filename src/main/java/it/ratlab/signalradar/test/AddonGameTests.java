// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.AddonCache;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.addon.detect.MotionTracker;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import it.ratlab.signalradar.data.StructureCacheData;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.recipe.RadarUpgradeRecipe;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.scan.ScanSettings;
import it.ratlab.signalradar.scan.ScanSnapshot;
import it.ratlab.signalradar.scan.StructureLookupService;
import it.ratlab.signalradar.target.Locator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 4 checks: addon registry, slots, menu safety, detectors, energy, command, upgrade. */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class AddonGameTests {
    private static final String EMPTY = "gametest_empty";
    private static final ScanSettings DEFAULTS = new ScanSettings(
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_RANGE, SignalRadarConfig.DEFAULT_RANGE, "range"),
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_FUZZ, SignalRadarConfig.DEFAULT_FUZZ, "fuzz"), 50, 5);

    private AddonGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(AddonGameTests.class));
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack radar(int tier, int energy) {
        ItemStack r = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(r, tier);
        RadarItem.setEnergy(r, energy);
        return r;
    }

    private static Item item(ResourceLocation id) {
        return AddonRegistry.item(id).orElseThrow(() -> new IllegalStateException("addon item not registered: " + id));
    }

    private static AddonSettings settings(ResourceLocation id) {
        return AddonSettings.defaults(AddonRegistry.get(id).orElseThrow());
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        return p;
    }

    private static List<ResourceLocation> ids(ResourceLocation... ids) {
        return List.of(ids);
    }

    private static boolean hasKey(List<Hit> hits, BlockPos abs) {
        String key = abs.getX() + "," + abs.getY() + "," + abs.getZ();
        return hits.stream().anyMatch(x -> x.key().equals(key));
    }

    private static boolean hasEntity(List<Hit> hits, Entity e) {
        return hits.stream().anyMatch(x -> x.key().equals(e.getUUID().toString()));
    }

    private static int addonItemCount(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof it.ratlab.signalradar.addon.AddonItem) {
                n += s.getCount();
            }
        }
        for (ItemStack s : p.getInventory().offhand) {
            if (s.getItem() instanceof it.ratlab.signalradar.addon.AddonItem) {
                n += s.getCount();
            }
        }
        if (p.containerMenu.getCarried().getItem() instanceof it.ratlab.signalradar.addon.AddonItem) {
            n += p.containerMenu.getCarried().getCount();
        }
        return n;
    }

    private static <T extends Entity> T spawnStill(GameTestHelper h, EntityType<T> type, BlockPos rel) {
        T e = h.spawn(type, rel);
        if (e instanceof Mob m) {
            m.setNoAi(true);
        }
        return e;
    }

    // ------------------------------------------------------------------ registry & config

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void builtinAddonsAreRegisteredWithSpecDefaults(GameTestHelper h) {
        // {min tier, radius min, radius max, refresh s, energy}
        Object[][] spec = {
                {AddonRegistry.CONTAINER, 0, 24, 48, 10, 10},
                {AddonRegistry.ORE, 1, 16, 32, 5, 15},
                {AddonRegistry.BIOSIGN, 1, 32, 64, 1, 10},
                {AddonRegistry.STRUCTURE, 1, 0, 0, 5, 10},
                {AddonRegistry.MOTION, 2, 24, 48, 1, 20},
        };
        for (Object[] row : spec) {
            ResourceLocation id = (ResourceLocation) row[0];
            item(id);
            AddonSettings s = AddonSettings.of(AddonRegistry.get(id).orElseThrow());
            h.assertTrue(s.enabled() && s.minTier() == (int) row[1] && s.radiusMin() == (int) row[2] && s.radiusMax() == (int) row[3]
                    && s.refreshSeconds() == (int) row[4] && s.energyCost() == (int) row[5], id + " defaults: " + s);
        }
        h.assertTrue(AddonSettings.defaults(AddonRegistry.get(AddonRegistry.ORE).orElseThrow()).radius(1, 512) == 16
                && AddonSettings.defaults(AddonRegistry.get(AddonRegistry.ORE).orElseThrow()).radius(4, 4096) == 32, "ore radius by tier");
        h.assertTrue(settings(AddonRegistry.STRUCTURE).radius(3, 2048) == 2048, "structure uses the tier range");
        h.assertTrue(AddonRegistry.get(AddonRegistry.MOTION).orElseThrow().category().equals("motion"), "motion category");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void lateOrDuplicateCustomAddonsAreRejectedWithoutCrash(GameTestHelper h) {
        h.assertTrue(AddonRegistry.isFrozen(), "registry should be frozen once items are registered");
        AddonDefinition late = AddonDefinition.builder(ResourceLocation.fromNamespaceAndPath("pack", "oil"), AddonDefinition.Detector.BLOCK_TAG)
                .tag(ResourceLocation.parse("c:ores/oil")).minTier(3).radius(16, 48).refresh(5).color(0x222222).category("Oil").build();
        h.assertTrue(!AddonRegistry.registerCustom(late), "late registration accepted");
        h.assertTrue(AddonRegistry.get(late.id()).isEmpty(), "late addon stored");
        boolean threw = false;
        try {
            AddonDefinition.builder(ResourceLocation.parse("pack:bad"), AddonDefinition.Detector.BLOCK_TAG).build();
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        h.assertTrue(threw, "tag detector without tag accepted");
        h.assertTrue(AddonDefinition.Detector.parsePublic("block_tag") == AddonDefinition.Detector.BLOCK_TAG
                && AddonDefinition.Detector.parsePublic("motion") == null, "public detector names");
        AddonDefinition needsMod = AddonDefinition.builder(ResourceLocation.parse("pack:x"), AddonDefinition.Detector.CONTAINER)
                .requiredMod("definitely_not_a_loaded_mod").build();
        h.assertTrue(!AddonRegistry.modPresent(needsMod), "missing mod reported present");
        h.assertTrue(AddonRegistry.modPresent(AddonDefinition.builder(ResourceLocation.parse("pack:y"), AddonDefinition.Detector.CONTAINER)
                .requiredMod("minecraft").build()), "loaded mod reported missing");
        h.succeed();
    }

    // ------------------------------------------------------------------ slots, tiers, refusal

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void slotCountFollowsTier(GameTestHelper h) {
        for (int t = 0; t <= 4; t++) {
            h.assertTrue(RadarItem.slots(t) == t + 1, "slots at tier " + t + " = " + RadarItem.slots(t));
        }
        h.assertTrue(RadarItem.slots(9) == 5 && RadarItem.slots(-3) == 1, "out of range tiers are clamped");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void minTierRefusesAddonsInRulesAndMenu(GameTestHelper h) {
        ItemStack r0 = radar(0, 100);
        h.assertTrue(AddonRules.installRefusal(r0, AddonRegistry.MOTION).isPresent(), "tier 0 accepted motion (min tier 2)");
        h.assertTrue(AddonRules.installRefusal(r0, AddonRegistry.ORE).isPresent(), "tier 0 accepted ore (min tier 1)");
        h.assertTrue(AddonRules.installRefusal(r0, AddonRegistry.CONTAINER).isEmpty(), "tier 0 refused container");
        h.assertTrue(AddonRules.installRefusal(radar(2, 0), AddonRegistry.MOTION).isEmpty(), "tier 2 refused motion");

        ServerPlayer p = player(h);
        ItemStack radar = radar(1, 500);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        h.assertTrue(menu.slotCount() == 2, "menu slots " + menu.slotCount());
        ItemStack motion = new ItemStack(item(AddonRegistry.MOTION));
        ItemStack ore = new ItemStack(item(AddonRegistry.ORE));
        var reason = menu.refusal(motion, 0);
        h.assertTrue(reason.isPresent() && reason.get().getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("gui.signalradar.addons.refusal.min_tier"), "no min tier reason: " + reason);
        h.assertTrue(!menu.getSlot(0).mayPlace(motion), "motion fits a tier 1 slot");
        h.assertTrue(menu.getSlot(0).mayPlace(ore), "ore refused at tier 1");
        h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "stone fits an addon slot");
        h.assertTrue(!menu.getSlot(2).mayPlace(ore) && menu.getSlot(2) instanceof AddonMenu.LockedSlot, "slot 2 should be locked at tier 1");
        // real clicks
        menu.setCarried(motion.copy());
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(!menu.getSlot(0).hasItem() && RadarItem.addons(radar).isEmpty(), "refused addon was placed");
        h.assertTrue(menu.getCarried().is(motion.getItem()), "refused addon left the cursor");
        menu.setCarried(ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    // ------------------------------------------------------------------ menu: write-back and no-dupe

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void menuWritesAddonsToRadarAndReloads(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(1, 500);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        ItemStack ore = new ItemStack(item(AddonRegistry.ORE));
        menu.setCarried(ore.copy());
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(0).getItem().is(ore.getItem()) && menu.getCarried().isEmpty(), "ore not placed");
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE)), "component " + RadarItem.addons(radar));
        // duplicate refused
        menu.setCarried(ore.copy());
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(!menu.getSlot(1).hasItem() && RadarItem.addons(radar).size() == 1, "duplicate installed");
        menu.setCarried(new ItemStack(item(AddonRegistry.CONTAINER)));
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE, AddonRegistry.CONTAINER)), "second addon " + RadarItem.addons(radar));
        menu.removed(p);
        // reopen: same content
        AddonMenu again = AddonMenu.create(2, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        h.assertTrue(again.getSlot(0).getItem().is(item(AddonRegistry.ORE)) && again.getSlot(1).getItem().is(item(AddonRegistry.CONTAINER)),
                "reopened menu lost addons");
        // taking one out removes it from the radar
        again.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(again.getCarried().is(item(AddonRegistry.ORE)) && RadarItem.addons(radar).equals(ids(AddonRegistry.CONTAINER)),
                "removal not written: " + RadarItem.addons(radar));
        again.setCarried(ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void menuQuickMoveConservesAddons(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(2, 500);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        p.getInventory().setItem(9, new ItemStack(item(AddonRegistry.ORE), 3));
        p.getInventory().setItem(10, new ItemStack(item(AddonRegistry.MOTION)));
        p.getInventory().setItem(11, new ItemStack(Items.STONE, 5));
        // inventory slot 9 = menu slot 5
        menu.quickMoveStack(p, 5);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE)), "quick move in: " + RadarItem.addons(radar));
        h.assertTrue(p.getInventory().getItem(9).getCount() == 2, "one ore should have moved, left " + p.getInventory().getItem(9));
        menu.quickMoveStack(p, 5); // duplicate: nothing more moves
        h.assertTrue(p.getInventory().getItem(9).getCount() == 2 && RadarItem.addons(radar).size() == 1, "duplicate quick-moved");
        menu.quickMoveStack(p, 6); // motion at tier 2 is allowed
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE, AddonRegistry.MOTION)), "motion: " + RadarItem.addons(radar));
        menu.quickMoveStack(p, 7); // stone never goes into addon slots
        h.assertTrue(p.getInventory().getItem(11).getCount() == 5, "stone moved");
        // out again: back into the player inventory, removed from the radar, total unchanged
        int before = addonItemCount(p) + 2; // 2 addons sit in the slots
        menu.quickMoveStack(p, 0);
        menu.quickMoveStack(p, 1);
        h.assertTrue(RadarItem.addons(radar).isEmpty(), "still installed " + RadarItem.addons(radar));
        h.assertTrue(addonItemCount(p) == before, "addons changed: " + addonItemCount(p) + " vs " + before);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void menuNeverDupesWhenRadarLeavesTheHand(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(1, 500);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        menu.setCarried(new ItemStack(item(AddonRegistry.ORE)));
        menu.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE)), "not installed");
        // the radar is dropped / swapped out of the hand
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(!menu.stillValid(p), "menu still valid without the radar");
        try {
            menu.clicked(0, 0, ClickType.PICKUP, p); // must be ignored (and close the menu)
        } catch (NullPointerException e) {
            // a mock player has no connection to send the close packet through; the click was still refused
        }
        h.assertTrue(menu.getSlot(0).hasItem() && menu.getCarried().isEmpty(), "click was processed after the radar left");
        try {
            menu.quickMoveStack(p, 0);
        } catch (NullPointerException ignored) {
            // see above
        }
        menu.removed(p);
        h.assertTrue(addonItemCount(p) == 0, "addon returned to the player although the radar still holds it: " + addonItemCount(p));
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE)), "radar lost the addon " + RadarItem.addons(radar));
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void menuCannotMoveTheRadarOutOfItsHand(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(1, 500);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        int selected = p.getInventory().selected;
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        int radarSlot = 5 + 27 + selected;
        h.assertTrue(menu.getSlot(radarSlot) instanceof AddonMenu.LockedSlot, "radar slot not locked");
        // swap with a hotbar key that is the selected slot, pick up, quick move, throw, double click
        p.getInventory().setItem(9, new ItemStack(Items.STONE));
        menu.clicked(5, selected, ClickType.SWAP, p);
        h.assertTrue(p.getMainHandItem() == radar, "swap moved the radar");
        menu.clicked(radarSlot, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty() && p.getMainHandItem() == radar, "picked up the radar");
        menu.clicked(radarSlot, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getMainHandItem() == radar, "quick moved the radar");
        menu.clicked(radarSlot, 0, ClickType.THROW, p);
        h.assertTrue(p.getMainHandItem() == radar, "threw the radar");
        menu.setCarried(new ItemStack(Items.STONE));
        menu.clicked(radarSlot, 0, ClickType.PICKUP, p);
        h.assertTrue(p.getMainHandItem() == radar && menu.getCarried().is(Items.STONE), "placed onto the radar slot");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(radarSlot, 0, ClickType.PICKUP_ALL, p);
        h.assertTrue(p.getMainHandItem() == radar, "double click collected the radar");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // offhand radar: the F key (swap with offhand) over any slot must do nothing
        ItemStack off = radar(1, 500);
        p.setItemInHand(InteractionHand.OFF_HAND, off);
        AddonMenu offMenu = AddonMenu.create(2, p.getInventory(), InteractionHand.OFF_HAND, off);
        offMenu.clicked(5, 40, ClickType.SWAP, p);
        h.assertTrue(p.getOffhandItem() == off, "offhand swap moved the radar");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void menuKeepsAddonsBeyondTheSlotsUntouched(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(0, 500); // 1 slot
        RadarItem.setAddons(radar, ids(AddonRegistry.CONTAINER, AddonRegistry.BIOSIGN));
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        h.assertTrue(menu.getSlot(0).getItem().is(item(AddonRegistry.CONTAINER)) && menu.slotCount() == 1, "first addon in slot 0");
        menu.removed(p);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.CONTAINER, AddonRegistry.BIOSIGN)), "overflow lost: " + RadarItem.addons(radar));
        // only the addons that fit the slots count for scanning
        h.assertTrue(AddonRules.active(radar).size() == 1 && AddonRules.active(radar).get(0).def().id().equals(AddonRegistry.CONTAINER),
                "active " + AddonRules.active(radar));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    // ------------------------------------------------------------------ upgrade, command

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradeKeepsAddons(GameTestHelper h) {
        ItemStack radar = radar(0, 777);
        RadarItem.setAddons(radar, ids(AddonRegistry.CONTAINER));
        List<ItemStack> grid = new ArrayList<>(List.of(radar, new ItemStack(ModItems.MODULE_1.get())));
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput in = CraftingInput.of(3, 3, grid);
        RadarUpgradeRecipe recipe = new RadarUpgradeRecipe(CraftingBookCategory.EQUIPMENT);
        ItemStack out = recipe.assemble(in, h.getLevel().registryAccess());
        h.assertTrue(RadarItem.tier(out) == 1 && RadarItem.energy(out) == 777, "tier/energy " + RadarItem.tier(out) + "/" + RadarItem.energy(out));
        h.assertTrue(RadarItem.addons(out).equals(ids(AddonRegistry.CONTAINER)), "addons lost in the upgrade: " + RadarItem.addons(out));
        h.assertTrue(RadarItem.slots(RadarItem.tier(out)) == 2, "upgrade gives a second slot");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void addonCommandRespectsTierSlotsAndDuplicates(GameTestHelper h) throws Exception {
        ServerPlayer p = player(h);
        ItemStack radar = radar(0, 10);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        var src = p.createCommandSourceStack().withPermission(2);
        dispatcher.execute("signalradar addon @s add signalradar:addon_container", src);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.CONTAINER)), "add: " + RadarItem.addons(radar));
        h.assertTrue(fails(dispatcher, "signalradar addon @s add signalradar:addon_container", src), "duplicate accepted");
        h.assertTrue(fails(dispatcher, "signalradar addon @s add signalradar:addon_ore", src), "ore (tier 1) accepted at tier 0");
        h.assertTrue(fails(dispatcher, "signalradar addon @s add signalradar:nope", src), "unknown addon accepted");
        RadarItem.setTier(radar, 1);
        dispatcher.execute("signalradar addon @s add signalradar:addon_ore", src);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.CONTAINER, AddonRegistry.ORE)), "second add: " + RadarItem.addons(radar));
        h.assertTrue(fails(dispatcher, "signalradar addon @s add signalradar:addon_biosign", src), "added into a full radar");
        dispatcher.execute("signalradar addon @s remove signalradar:addon_container", src);
        h.assertTrue(RadarItem.addons(radar).equals(ids(AddonRegistry.ORE)), "remove: " + RadarItem.addons(radar));
        h.assertTrue(fails(dispatcher, "signalradar addon @s remove signalradar:addon_container", src), "removed an absent addon");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(fails(dispatcher, "signalradar addon @s add signalradar:addon_container", src), "no radar in hand accepted");
        h.succeed();
    }

    private static boolean fails(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d, String cmd,
                                 net.minecraft.commands.CommandSourceStack src) {
        try {
            d.execute(cmd, src);
            return false;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------ energy and blips

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void scanEnergyIsBaseCostPlusAddonCosts(GameTestHelper h) {
        ItemStack radar = radar(2, 1000);
        RadarItem.setAddons(radar, ids(AddonRegistry.CONTAINER, AddonRegistry.ORE, AddonRegistry.MOTION));
        List<AddonSettings> active = AddonRules.active(radar);
        h.assertTrue(active.size() == 3 && AddonRules.energyCost(active) == 10 + 15 + 20, "active " + active.size());
        UUID id = new UUID(1, 2);
        ScanSnapshot s = RadarScanner.scan(radar, id, Vec3.ZERO, 100, DEFAULTS, List.of(), d -> java.util.Optional.empty(), active, a -> List.of(),
                RadarScanner.Charge.PAY);
        h.assertTrue(!s.noSignal() && s.charged() && RadarItem.energy(radar) == 1000 - 50 - 45, "energy " + RadarItem.energy(radar));
        // free refresh between charges
        ScanSnapshot free = RadarScanner.scan(radar, id, Vec3.ZERO, 120, DEFAULTS, List.of(), d -> java.util.Optional.empty(), active, a -> List.of(),
                RadarScanner.Charge.FREE);
        h.assertTrue(!free.noSignal() && !free.charged() && RadarItem.energy(radar) == 905, "free scan charged: " + RadarItem.energy(radar));
        // 94 FE cannot pay 95: NO SIGNAL, nothing taken
        RadarItem.setEnergy(radar, 94);
        ScanSnapshot broke = RadarScanner.scan(radar, id, Vec3.ZERO, 200, DEFAULTS, List.of(), d -> java.util.Optional.empty(), active, a -> List.of(),
                RadarScanner.Charge.PAY);
        h.assertTrue(broke.noSignal() && broke.blips().isEmpty() && RadarItem.energy(radar) == 94, "no signal expected");
        // disabled / too-high-tier addons are not active and cost nothing
        ItemStack low = radar(0, 1000);
        RadarItem.setAddons(low, ids(AddonRegistry.MOTION, AddonRegistry.CONTAINER));
        h.assertTrue(AddonRules.active(low).size() == 1 && AddonRules.active(low).get(0).def().id().equals(AddonRegistry.CONTAINER),
                "tier 0 radar counted a tier 2 addon");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void addonBlipsUseAddonCategoryNameAndFuzz(GameTestHelper h) {
        UUID id = new UUID(5, 6);
        Component name = Component.literal("Chest");
        List<Hit> hits = List.of(new Hit("1,2,3", name, 40, 64, 0, 0), new Hit("9,9,9", Component.literal("Ore"), 30, 64, 0, 0x123456));
        ItemStack radar = radar(0, 5000);
        RadarItem.setAddons(radar, ids(AddonRegistry.CONTAINER));
        ScanSnapshot s = RadarScanner.scan(radar, id, new Vec3(0, 64, 0), 100, DEFAULTS, List.of(), d -> java.util.Optional.empty(),
                AddonRules.active(radar), a -> hits, RadarScanner.Charge.PAY);
        h.assertTrue(s.blips().size() == 2, "blips " + s.blips().size());
        Blip b = s.blips().get(0);
        h.assertTrue(b.category().equals("container") && b.name() == name && !b.outOfRange() && b.color() == 0xE0A040, "blip " + b);
        h.assertTrue(b.id().equals("signalradar:addon_container/1,2,3"), "blip id " + b.id());
        h.assertTrue(s.blips().get(1).color() == 0x123456, "hit colour ignored");
        double off = Math.hypot(b.x() - 40, b.z());
        h.assertTrue(off <= 64.0 * 40 / 256 + 1e-6, "fuzz " + off + " exceeds the tier 0 bound");
        // tier 4 = exact
        RadarItem.setTier(radar, 4);
        ScanSnapshot exact = RadarScanner.scan(radar, id, new Vec3(0, 64, 0), 100, DEFAULTS, List.of(), d -> java.util.Optional.empty(),
                AddonRules.active(radar), a -> hits, RadarScanner.Charge.PAY);
        h.assertTrue(exact.blips().get(0).x() == 40 && exact.blips().get(0).z() == 0, "tier 4 fuzz");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void addonCacheHonoursRefreshRadiusAndPlayer(GameTestHelper h) {
        AddonCache cache = new AddonCache();
        AtomicInteger runs = new AtomicInteger();
        java.util.function.Supplier<List<Hit>> compute = () -> {
            runs.incrementAndGet();
            return List.of();
        };
        UUID a = new UUID(1, 1);
        ResourceLocation addon = AddonRegistry.ORE;
        cache.get(a, addon, 100, 100, 16, "overworld", compute);
        cache.get(a, addon, 199, 100, 16, "overworld", compute);
        h.assertTrue(runs.get() == 1, "recomputed inside the refresh window");
        cache.get(a, addon, 200, 100, 16, "overworld", compute);
        h.assertTrue(runs.get() == 2, "not recomputed after the refresh window");
        cache.get(a, addon, 201, 100, 32, "overworld", compute);
        h.assertTrue(runs.get() == 3, "radius change ignored");
        cache.get(a, addon, 202, 100, 32, "the_nether", compute);
        h.assertTrue(runs.get() == 4, "dimension change ignored");
        cache.get(new UUID(2, 2), addon, 202, 100, 32, "the_nether", compute);
        h.assertTrue(runs.get() == 5, "cache shared between players");
        cache.get(a, AddonRegistry.CONTAINER, 202, 100, 32, "the_nether", compute);
        h.assertTrue(runs.get() == 6, "cache shared between addons");
        h.succeed();
    }

    // ------------------------------------------------------------------ detectors

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void containerDetectorFindsInventoriesOnly(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        h.setBlock(new BlockPos(3, 1, 1), Blocks.BARREL);
        h.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
        h.setBlock(new BlockPos(1, 1, 3), Blocks.FURNACE);
        h.setBlock(new BlockPos(-2, 1, 1), Blocks.CRAFTING_TABLE);
        List<Hit> hits = Detectors.run(settings(AddonRegistry.CONTAINER), p, h.getLevel(), 24, BlockLocatorScan.Budget.unlimited(), 100);
        h.assertTrue(hasKey(hits, h.absolutePos(new BlockPos(2, 1, 1))), "chest missed");
        h.assertTrue(hasKey(hits, h.absolutePos(new BlockPos(3, 1, 1))), "barrel missed");
        h.assertTrue(hasKey(hits, h.absolutePos(new BlockPos(1, 1, 3))), "furnace (Container) missed");
        h.assertTrue(!hasKey(hits, h.absolutePos(new BlockPos(1, 1, 2))), "stone reported");
        h.assertTrue(!hasKey(hits, h.absolutePos(new BlockPos(-2, 1, 1))), "crafting table reported");
        Hit chest = hits.stream().filter(x -> x.key().equals(key(h.absolutePos(new BlockPos(2, 1, 1))))).findFirst().orElseThrow();
        h.assertTrue(chest.name().getString().equals(Blocks.CHEST.getName().getString()), "name " + chest.name().getString());
        // outside the radius: not reported
        h.assertTrue(Detectors.run(settings(AddonRegistry.CONTAINER), p, h.getLevel(), 1, BlockLocatorScan.Budget.unlimited(), 100).stream()
                .noneMatch(x -> x.key().equals(key(h.absolutePos(new BlockPos(3, 1, 1))))), "radius ignored");
        h.succeed();
    }

    private static String key(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void oreDetectorMergesVeinsAndColoursByMapColor(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(1, 2, 0), Blocks.DIAMOND_ORE);
        h.setBlock(new BlockPos(2, 2, 0), Blocks.DIAMOND_ORE); // same vein: merged
        h.setBlock(new BlockPos(1, 2, 3), Blocks.NETHER_QUARTZ_ORE); // other ore, different map colour
        h.setBlock(new BlockPos(9, 2, 0), Blocks.DIAMOND_ORE); // far from the first: own blip
        h.setBlock(new BlockPos(-3, 2, 0), Blocks.STONE);
        List<Hit> hits = Detectors.run(settings(AddonRegistry.ORE), p, h.getLevel(), 16, BlockLocatorScan.Budget.unlimited(), 100);
        BlockPos d1 = h.absolutePos(new BlockPos(1, 2, 0));
        BlockPos d2 = h.absolutePos(new BlockPos(2, 2, 0));
        BlockPos q = h.absolutePos(new BlockPos(1, 2, 3));
        h.assertTrue(hasKey(hits, d1) && !hasKey(hits, d2), "vein not merged into its nearest block");
        h.assertTrue(hasKey(hits, q) && hasKey(hits, h.absolutePos(new BlockPos(9, 2, 0))), "ore missed");
        h.assertTrue(!hasKey(hits, h.absolutePos(new BlockPos(-3, 2, 0))), "stone reported");
        Hit dia = hits.stream().filter(x -> x.key().equals(key(d1))).findFirst().orElseThrow();
        Hit quartz = hits.stream().filter(x -> x.key().equals(key(q))).findFirst().orElseThrow();
        int expectedDia = h.getLevel().getBlockState(d1).getMapColor(h.getLevel(), d1).col;
        int expectedQuartz = h.getLevel().getBlockState(q).getMapColor(h.getLevel(), q).col;
        h.assertTrue(dia.color() == expectedDia && dia.color() != 0, "diamond colour " + Integer.toHexString(dia.color()));
        h.assertTrue(quartz.color() == expectedQuartz && quartz.color() != dia.color(), "quartz colour " + Integer.toHexString(quartz.color()));
        h.assertTrue(dia.name().getString().equals(Blocks.DIAMOND_ORE.getName().getString()), "ore name");
        // no budget: nothing searched
        h.assertTrue(Detectors.run(settings(AddonRegistry.ORE), p, h.getLevel(), 16, new BlockLocatorScan.Budget(0), 100).isEmpty(),
                "ore search ignored the budget");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void biosignDetectorFindsPassiveMobsAndVillagers(GameTestHelper h) {
        ServerPlayer p = player(h);
        Entity pig = spawnStill(h, EntityType.PIG, new BlockPos(2, 1, 1));
        Entity villager = spawnStill(h, EntityType.VILLAGER, new BlockPos(3, 1, 1));
        Entity cod = spawnStill(h, EntityType.BAT, new BlockPos(1, 2, 2));
        Entity zombie = spawnStill(h, EntityType.ZOMBIE, new BlockPos(1, 1, 3));
        Entity stand = h.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 1, 3));
        List<Hit> hits = Detectors.run(settings(AddonRegistry.BIOSIGN), p, h.getLevel(), 32, BlockLocatorScan.Budget.unlimited(), 100);
        h.assertTrue(hasEntity(hits, pig), "pig missed");
        h.assertTrue(hasEntity(hits, villager), "villager missed");
        h.assertTrue(hasEntity(hits, cod), "ambient mob missed");
        h.assertTrue(!hasEntity(hits, zombie), "zombie reported as biosign");
        h.assertTrue(!hasEntity(hits, stand), "armor stand reported");
        h.assertTrue(hits.stream().noneMatch(x -> x.key().equals(p.getUUID().toString())), "the player itself reported");
        h.assertTrue(!hasEntity(Detectors.run(settings(AddonRegistry.BIOSIGN), p, h.getLevel(), 1, BlockLocatorScan.Budget.unlimited(), 100), villager),
                "biosign radius ignored");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void motionDetectorShowsOnlyMovingHostiles(GameTestHelper h) {
        ServerPlayer p = player(h);
        MotionTracker.INSTANCE.forget(p.getUUID());
        Mob walker = spawnStill(h, EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        Mob still = spawnStill(h, EntityType.ZOMBIE, new BlockPos(-3, 1, 1));
        Mob pig = spawnStill(h, EntityType.PIG, new BlockPos(1, 1, 3));
        AddonSettings motion = settings(AddonRegistry.MOTION);
        List<Hit> first = Detectors.run(motion, p, h.getLevel(), 48, BlockLocatorScan.Budget.unlimited(), 100);
        h.assertTrue(!hasEntity(first, walker) && !hasEntity(first, still), "first sample cannot know who moves");
        walker.setPos(walker.getX() + 2, walker.getY(), walker.getZ());
        pig.setPos(pig.getX() + 2, pig.getY(), pig.getZ()); // moving but not hostile
        still.setPos(still.getX() + 0.05, still.getY(), still.getZ()); // jitter below the threshold
        List<Hit> second = Detectors.run(motion, p, h.getLevel(), 48, BlockLocatorScan.Budget.unlimited(), 120);
        h.assertTrue(hasEntity(second, walker), "moving zombie missed");
        h.assertTrue(!hasEntity(second, still), "still zombie reported");
        h.assertTrue(!hasEntity(second, pig), "moving pig reported by the motion tracker");
        // standing still again: gone
        List<Hit> third = Detectors.run(motion, p, h.getLevel(), 48, BlockLocatorScan.Budget.unlimited(), 140);
        h.assertTrue(!hasEntity(third, walker), "zombie that stopped is still shown");
        walker.discard();
        still.discard();
        MotionTracker.INSTANCE.forget(p.getUUID());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void structureDetectorUsesCacheAndQueue(GameTestHelper h) {
        ServerPlayer p = player(h);
        StructureLookupService.INSTANCE.clearQueue();
        // the shipped tag is empty: nothing to do, nothing queued
        h.assertTrue(Detectors.run(settings(AddonRegistry.STRUCTURE), p, h.getLevel(), 512, BlockLocatorScan.Budget.unlimited(), 100).isEmpty(),
                "empty tag produced hits");
        h.assertTrue(StructureLookupService.INSTANCE.pending() == 0, "empty tag queued searches");
        // a custom structure_tag addon on #minecraft:village: uncached entries are queued, a cached one is shown
        AddonDefinition def = AddonDefinition.builder(ResourceLocation.parse("pack:villages"), AddonDefinition.Detector.STRUCTURE_TAG)
                .tag(ResourceLocation.parse("minecraft:village")).radius(0, 0).build();
        StructureCacheData data = StructureCacheData.get(h.getLevel().getServer());
        data.clear();
        Locator.Structure plains = new Locator.Structure(ResourceLocation.parse("minecraft:village_plains"), false, 512 / 16);
        BlockPos at = p.blockPosition().offset(40, 0, 20);
        data.putFound(StructureCacheData.key(h.getLevel().dimension(), plains), at, 0);
        List<Hit> hits = Detectors.run(AddonSettings.defaults(def), p, h.getLevel(), 512, BlockLocatorScan.Budget.unlimited(), 100);
        h.assertTrue(hits.size() == 1, "expected the one cached structure, got " + hits.size());
        Hit hit = hits.get(0);
        h.assertTrue(hit.key().equals("minecraft:village_plains") && hit.name().getString().equals("Village Plains"), "hit " + hit);
        h.assertTrue(Math.abs(hit.x() - (at.getX() + 0.5)) < 1e-6 && hit.y() == p.getY(), "position");
        h.assertTrue(StructureLookupService.INSTANCE.pending() >= 1, "uncached structures were not queued");
        // out of radius: hidden
        h.assertTrue(Detectors.run(AddonSettings.defaults(def), p, h.getLevel(), 16, BlockLocatorScan.Budget.unlimited(), 100).isEmpty(),
                "structure beyond the radius shown");
        data.clear();
        StructureLookupService.INSTANCE.clearQueue();
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void endToEndScanWithInstalledAddons(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        Entity pig = spawnStill(h, EntityType.PIG, new BlockPos(1, 1, 3));
        ItemStack radar = radar(4, 5000); // tier 4: no fuzz, exact positions
        RadarItem.setAddons(radar, ids(AddonRegistry.CONTAINER, AddonRegistry.BIOSIGN));
        long now = 100;
        var detect = (java.util.function.Function<AddonSettings, List<Hit>>) a -> Detectors.run(a, p, h.getLevel(),
                a.radius(4, DEFAULTS.range(4)), BlockLocatorScan.Budget.unlimited(), now);
        ScanSnapshot s = RadarScanner.scan(radar, p.getUUID(), p.position(), now, DEFAULTS, List.of(), d -> java.util.Optional.empty(),
                AddonRules.active(radar), detect, RadarScanner.Charge.PAY);
        h.assertTrue(RadarItem.energy(radar) == 5000 - 50 - 20, "energy " + RadarItem.energy(radar));
        BlockPos chest = h.absolutePos(new BlockPos(2, 1, 1));
        h.assertTrue(s.blips().stream().anyMatch(b -> b.category().equals("container") && b.x() == chest.getX() + 0.5 && b.z() == chest.getZ() + 0.5),
                "chest blip missing");
        h.assertTrue(s.blips().stream().anyMatch(b -> b.category().equals("biosign") && b.id().endsWith(pig.getUUID().toString())), "pig blip missing");
        h.succeed();
    }
}
