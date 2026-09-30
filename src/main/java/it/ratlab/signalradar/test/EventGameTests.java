// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import it.ratlab.signalradar.api.RadarAddonChangedEvent;
import it.ratlab.signalradar.api.RadarScanEvent;
import it.ratlab.signalradar.api.RadarUpgradedEvent;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.Blip;
import it.ratlab.signalradar.scan.ScanHandler;
import it.ratlab.signalradar.scan.ScanSettings;
import it.ratlab.signalradar.scan.ScanSnapshot;
import it.ratlab.signalradar.target.Locator;
import it.ratlab.signalradar.target.TargetDef;
import it.ratlab.signalradar.target.TargetManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 7 core events (no KubeJS needed): {@link RadarScanEvent} edits / cancels the snapshot, {@link RadarUpgradedEvent}
 * from the crafting event, {@link RadarAddonChangedEvent} from the addon GUI and the command. Run in every game test run.
 */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class EventGameTests {
    private static final String EMPTY = "gametest_empty";
    static final ScanSettings DEFAULTS = new ScanSettings(
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_RANGE, SignalRadarConfig.DEFAULT_RANGE, "range"),
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_FUZZ, SignalRadarConfig.DEFAULT_FUZZ, "fuzz"), 50, 5);
    static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    static final ResourceLocation VISIBLE = SignalRadar.id("kjs_visible");
    static final ResourceLocation HIDDEN = SignalRadar.id("kjs_hidden");
    static final ResourceLocation LOCKED = SignalRadar.id("kjs_locked");

    private EventGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(EventGameTests.class));
    }

    // ------------------------------------------------------------------ helpers (shared with KubeJSGameTests)

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        return p;
    }

    static ItemStack radar(int tier, int energy) {
        ItemStack r = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(r, tier);
        RadarItem.setEnergy(r, energy);
        return r;
    }

    /** Three pos targets near the player: kjs_visible (+30 x), kjs_hidden (+20 z), kjs_locked (needs an unlock). */
    static void withTargets(ServerPlayer p, Runnable body) {
        BlockPos base = p.blockPosition();
        Map<ResourceLocation, TargetDef> now = new TreeMap<>();
        now.put(VISIBLE, target(VISIBLE, base.offset(30, 0, 0), false));
        now.put(HIDDEN, target(HIDDEN, base.offset(0, 0, 20), false));
        now.put(LOCKED, target(LOCKED, base.offset(-25, 0, 0), true));
        Map<ResourceLocation, TargetDef> before = new TreeMap<>();
        TargetManager.all().forEach(d -> before.put(d.id(), d));
        TargetManager.setForTest(now);
        try {
            body.run();
        } finally {
            TargetManager.setForTest(before);
        }
    }

    private static TargetDef target(ResourceLocation id, BlockPos pos, boolean unlock) {
        return new TargetDef(id, Component.literal("Name " + id.getPath()), "narrative", 0, 0x7CFC00, 128, null, unlock, 4, false,
                new Locator.Pos(pos, OVERWORLD));
    }

    static ScanSnapshot scan(ServerPlayer p, ItemStack radar) {
        return ScanHandler.computeScan(p, radar, DEFAULTS, p.level().getGameTime());
    }

    static Blip blip(ScanSnapshot s, String id) {
        return s.blips().stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
    }

    /** Runs {@code body} with a temporary NeoForge listener. */
    static <E extends Event> void listening(Class<E> type, Consumer<E> listener, Runnable body) {
        Consumer<E> l = e -> {
            if (type.isInstance(e)) {
                listener.accept(e);
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.NORMAL, false, type, l);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
    }

    /** A crafting grid with the given stacks, as the crafted event sees it. */
    static SimpleContainer grid(ItemStack... stacks) {
        SimpleContainer c = new SimpleContainer(9);
        for (int i = 0; i < stacks.length; i++) {
            c.setItem(i, stacks[i]);
        }
        return c;
    }

    static void command(ServerPlayer p, String cmd) {
        try {
            p.getServer().getCommands().getDispatcher().execute(cmd, p.createCommandSourceStack().withPermission(2));
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("command failed: " + cmd + ": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ tests

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void scanEventEditsAndCancels(GameTestHelper h) {
        ServerPlayer p = player(h);
        withTargets(p, () -> {
            ItemStack r = radar(2, 10000);
            ScanSnapshot plain = scan(p, r);
            h.assertTrue(blip(plain, VISIBLE.toString()) != null && blip(plain, HIDDEN.toString()) != null
                    && blip(plain, LOCKED.toString()) == null, "plain scan " + plain.blips());

            List<RadarScanEvent> seen = new ArrayList<>();
            listening(RadarScanEvent.class, e -> {
                seen.add(e);
                h.assertTrue(e.getTier() == 2 && e.getRange() == DEFAULTS.range(2) && e.getRadar() == r, "event fields");
                h.assertTrue(e.removeTarget(HIDDEN.toString()) == 1, "remove by id");
                e.addTarget(Component.literal("Beacon"), 0x12345678, p.getX() + 10, p.getY(), p.getZ());
                e.addTarget(Component.literal("Beacon"), 0xFF0000, p.getX() + 5000, p.getY(), p.getZ());
            }, () -> {
                ScanSnapshot s = scan(p, r);
                h.assertTrue(seen.size() == 1, "one scan event, got " + seen.size());
                h.assertTrue(blip(s, HIDDEN.toString()) == null && blip(s, VISIBLE.toString()) != null, "hidden removed " + s.blips());
                Blip b = blip(s, "script/Beacon");
                h.assertTrue(b != null && b.color() == 0x345678 && b.category().equals(RadarScanEvent.SCRIPT_CATEGORY)
                        && Math.abs(b.x() - (p.getX() + 10)) < 1e-6 && !b.outOfRange() && b.name().getString().equals("Beacon"),
                        "script blip exact, in range: " + b);
                Blip far = blip(s, "script/Beacon#2");
                h.assertTrue(far != null && far.outOfRange(), "second blip gets a unique id and is out of range: " + far);
                h.assertTrue(!s.noSignal() && s.charged(), "still a signal");
            });

            int before = RadarItem.energy(r);
            listening(RadarScanEvent.class, e -> e.setCanceled(true), () -> {
                ScanSnapshot s = scan(p, r);
                h.assertTrue(s.noSignal() && s.blips().isEmpty(), "cancelled = NO SIGNAL " + s);
                h.assertTrue(RadarItem.energy(r) < before, "energy still charged");
            });

            // no event for a radar that cannot pay
            ItemStack empty = radar(2, 0);
            List<RadarScanEvent> none = new ArrayList<>();
            listening(RadarScanEvent.class, none::add, () -> h.assertTrue(scan(p, empty).noSignal(), "empty radar"));
            h.assertTrue(none.isEmpty(), "no scan event without signal");
        });
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradedEventFromCrafting(GameTestHelper h) {
        ServerPlayer p = player(h);
        List<RadarUpgradedEvent> seen = new ArrayList<>();
        listening(RadarUpgradedEvent.class, seen::add, () -> {
            ItemStack in = radar(1, 500);
            ItemStack out = in.copy();
            RadarItem.setTier(out, 2);
            EventHooks.firePlayerCraftingEvent(p, out, grid(in, new ItemStack(ModItems.MODULE_2.get())));
            // not the upgrade recipe: wrong module, two radars, a radar crafted from something else
            EventHooks.firePlayerCraftingEvent(p, out, grid(in, new ItemStack(ModItems.MODULE_3.get())));
            EventHooks.firePlayerCraftingEvent(p, out, grid(in, in.copy(), new ItemStack(ModItems.MODULE_2.get())));
            EventHooks.firePlayerCraftingEvent(p, radar(0, 0), grid(new ItemStack(ModItems.MODULE_1.get())));
        });
        h.assertTrue(seen.size() == 1, "one upgrade event, got " + seen.size());
        RadarUpgradedEvent e = seen.get(0);
        h.assertTrue(e.getOldTier() == 1 && e.getNewTier() == 2 && RadarItem.tier(e.getRadar()) == 2 && e.getPlayer() == p,
                "tiers 1 -> 2");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void addonChangedEventFromMenuAndCommand(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack r = radar(2, 1000);
        p.setItemInHand(InteractionHand.MAIN_HAND, r);
        ItemStack ore = new ItemStack(AddonRegistry.item(AddonRegistry.ORE).orElseThrow());
        ItemStack bio = new ItemStack(AddonRegistry.item(AddonRegistry.BIOSIGN).orElseThrow());
        List<String> seen = new ArrayList<>();
        listening(RadarAddonChangedEvent.class, e -> seen.add(e.getSlot() + ":" + e.getOldAddon() + ">" + e.getNewAddon()), () -> {
            AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, r);
            menu.addonContainer().setItem(1, ore.copy());
            menu.addonContainer().setItem(0, bio.copy());
            menu.addonContainer().setItem(1, ItemStack.EMPTY);
            h.assertTrue(RadarItem.addons(r).equals(List.of(AddonRegistry.BIOSIGN)), "menu wrote " + RadarItem.addons(r));
            menu.addonContainer().setItem(0, ItemStack.EMPTY);
            command(p, "signalradar addon @s add signalradar:addon_ore");
            command(p, "signalradar addon @s add signalradar:addon_biosign");
            command(p, "signalradar addon @s remove signalradar:addon_ore");
        });
        List<String> expected = List.of(
                "1:null>signalradar:addon_ore",
                "0:null>signalradar:addon_biosign",
                "1:signalradar:addon_ore>null",
                "0:signalradar:addon_biosign>null",
                "0:null>signalradar:addon_ore",
                "1:null>signalradar:addon_biosign",
                "0:signalradar:addon_ore>null");
        h.assertTrue(seen.equals(expected), "addon events " + seen);
        h.assertTrue(RadarItem.addons(r).equals(List.of(AddonRegistry.BIOSIGN)), "command wrote " + RadarItem.addons(r));
        h.succeed();
    }
}
