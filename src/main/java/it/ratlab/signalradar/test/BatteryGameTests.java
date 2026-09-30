// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.addon.AddonConfig;
import it.ratlab.signalradar.addon.AddonDefinition;
import it.ratlab.signalradar.addon.AddonEntry;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModComponents;
import it.ratlab.signalradar.registry.ModItems;
import it.ratlab.signalradar.scan.RadarScanner;
import it.ratlab.signalradar.scan.ScanSettings;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Battery addon (stackable, extra capacity), unlimited charging, addon component format, configurable slot counts. */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatteryGameTests {
    private static final String EMPTY = "gametest_empty";
    private static final ScanSettings DEFAULTS = new ScanSettings(
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_RANGE, SignalRadarConfig.DEFAULT_RANGE, "range"),
            SignalRadarConfig.sanitizeTierList(SignalRadarConfig.DEFAULT_FUZZ, SignalRadarConfig.DEFAULT_FUZZ, "fuzz"), 50, 5);

    private BatteryGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(BatteryGameTests.class));
    }

    private static Item battery() {
        return AddonRegistry.item(AddonRegistry.BATTERY).orElseThrow(() -> new IllegalStateException("no battery item"));
    }

    private static Item addon(ResourceLocation id) {
        return AddonRegistry.item(id).orElseThrow(() -> new IllegalStateException("no addon item " + id));
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        return p;
    }

    private static ItemStack radar(int tier) {
        ItemStack r = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(r, tier);
        return r;
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void batteryDefinitionAndItem(GameTestHelper h) {
        AddonDefinition def = AddonRegistry.get(AddonRegistry.BATTERY).orElseThrow();
        AddonSettings s = AddonSettings.of(def);
        h.assertTrue(def.stackable() && def.detector() == AddonDefinition.Detector.NONE, "battery definition " + def);
        h.assertTrue(s.minTier() == 0 && s.energyCost() == 0 && s.enabled(), "battery settings " + s);
        h.assertTrue(new ItemStack(battery()).getMaxStackSize() == AddonRegistry.BATTERY_STACK, "battery stacks to 8");
        h.assertTrue(new ItemStack(addon(AddonRegistry.ORE)).getMaxStackSize() == 16, "other addons keep 16");
        h.assertTrue(!AddonRegistry.get(AddonRegistry.ORE).orElseThrow().stackable(), "ore is not stackable");
        h.assertTrue(AddonConfig.capacityPerBattery() == AddonConfig.DEFAULT_BATTERY_CAPACITY, "default capacity per battery 10000");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void batteryCapacityMath(GameTestHelper h) {
        int base = SignalRadarConfig.capacity();
        int per = AddonConfig.capacityPerBattery();
        h.assertTrue(RadarItem.capacity(20000, 3, 10000) == 50000, "20000 + 3 * 10000");
        h.assertTrue(RadarItem.capacity(Integer.MAX_VALUE, 8, 10000) == Integer.MAX_VALUE, "saturates");
        h.assertTrue(RadarItem.capacity(0, 0, 0) == 1, "never 0");

        ItemStack radar = radar(0);
        h.assertTrue(RadarItem.capacity(radar) == base, "no battery: base capacity");
        RadarItem.setAddonEntries(radar, List.of(new AddonEntry(AddonRegistry.BATTERY, 3)));
        h.assertTrue(AddonRules.batteries(radar) == 3, "3 batteries counted, got " + AddonRules.batteries(radar));
        int cap = base + 3 * per;
        h.assertTrue(RadarItem.capacity(radar) == cap, "capacity " + RadarItem.capacity(radar) + " expected " + cap);
        RadarItem.setEnergy(radar, Integer.MAX_VALUE);
        h.assertTrue(RadarItem.energy(radar) == cap, "setEnergy clamps to the battery capacity: " + RadarItem.energy(radar));
        IEnergyStorage fe = radar.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(fe != null && fe.getMaxEnergyStored() == cap && fe.getEnergyStored() == cap, "FE capability capacity");
        h.assertTrue(RadarScanner.noSignal(radar, DEFAULTS, 0, false).capacity() == cap, "snapshot capacity");
        h.assertTrue(radar.getItem().getBarWidth(radar) == 13, "full bar");

        // a battery beyond the tier's slots does not count
        ItemStack two = radar(0); // 1 slot
        RadarItem.setAddonEntries(two, List.of(AddonEntry.of(AddonRegistry.CONTAINER), new AddonEntry(AddonRegistry.BATTERY, 8)));
        h.assertTrue(AddonRules.batteries(two) == 0 && RadarItem.capacity(two) == base, "overflow battery counted");

        // removing batteries clamps the stored energy (the extra FE is lost)
        RadarItem.setAddonEntries(radar, List.of(new AddonEntry(AddonRegistry.BATTERY, 1)));
        h.assertTrue(RadarItem.energy(radar) == base + per, "one battery left: " + RadarItem.energy(radar));
        RadarItem.setAddonEntries(radar, List.of());
        h.assertTrue(RadarItem.energy(radar) == base && radar.get(ModComponents.ENERGY.get()) == base, "raw energy clamped: "
                + radar.get(ModComponents.ENERGY.get()));
        RadarItem.setAddonEntries(radar, List.of(new AddonEntry(AddonRegistry.BATTERY, 2)));
        h.assertTrue(RadarItem.energy(radar) == base, "energy does not come back with the batteries");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void maxReceiveIsUnlimitedByDefault(GameTestHelper h) {
        h.assertTrue(SignalRadarConfig.defaultMaxReceive() == Integer.MAX_VALUE, "default maxReceive " + SignalRadarConfig.defaultMaxReceive());
        // the dev run folders may still hold an old config file with 100: test the default value
        SignalRadarConfig.overrideMaxReceive(SignalRadarConfig.defaultMaxReceive());
        try {
            unlimitedReceive(h);
        } finally {
            SignalRadarConfig.overrideMaxReceive(-1);
        }
        h.succeed();
    }

    private static void unlimitedReceive(GameTestHelper h) {
        ItemStack radar = radar(0);
        RadarItem.setAddonEntries(radar, List.of(new AddonEntry(AddonRegistry.BATTERY, 2)));
        int cap = RadarItem.capacity(radar);
        IEnergyStorage fe = radar.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(fe != null, "no FE capability");
        int sim = fe.receiveEnergy(Integer.MAX_VALUE, true);
        h.assertTrue(sim == cap && RadarItem.energy(radar) == 0, "simulate " + sim);
        int got = fe.receiveEnergy(Integer.MAX_VALUE, false);
        h.assertTrue(got == cap && RadarItem.energy(radar) == cap, "one insert fills the radar: " + got + " / " + cap);
        h.assertTrue(fe.receiveEnergy(1000, false) == 0, "full radar accepts nothing");
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void batteriesStackInOneMenuSlot(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack radar = radar(1); // 2 slots
        RadarItem.setEnergy(radar, 100);
        p.setItemInHand(InteractionHand.MAIN_HAND, radar);
        AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        p.getInventory().setItem(9, new ItemStack(battery(), 5));
        p.getInventory().setItem(10, new ItemStack(battery(), 6));
        // inventory slot 9 = menu slot 5 (5 addon slots first)
        menu.quickMoveStack(p, AddonMenu.MAX_SLOTS);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 5 && p.getInventory().getItem(9).isEmpty(), "5 batteries in slot 0");
        h.assertTrue(AddonRules.count(radar, AddonRegistry.BATTERY) == 5, "component count " + RadarItem.addonEntries(radar));
        // shift-click merges into the same slot up to 8, never a second battery slot
        menu.quickMoveStack(p, AddonMenu.MAX_SLOTS + 1);
        h.assertTrue(menu.getSlot(0).getItem().getCount() == 8 && !menu.getSlot(1).hasItem(), "merge to 8, slot 1 "
                + menu.getSlot(1).getItem());
        h.assertTrue(p.getInventory().getItem(10).getCount() == 3, "3 left in the inventory: " + p.getInventory().getItem(10));
        h.assertTrue(AddonRules.count(radar, AddonRegistry.BATTERY) == 8
                && RadarItem.capacity(radar) == SignalRadarConfig.capacity() + 8 * AddonConfig.capacityPerBattery(), "8 batteries");
        // a second battery slot is refused by click too
        menu.setCarried(new ItemStack(battery(), 3));
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(!menu.getSlot(1).hasItem() && menu.getCarried().getCount() == 3, "battery went into a second slot");
        menu.setCarried(ItemStack.EMPTY);
        // non-stackable addons still hold one
        menu.setCarried(new ItemStack(addon(AddonRegistry.CONTAINER), 4));
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(1).getItem().getCount() == 1 && menu.getCarried().getCount() == 3, "container stacked: "
                + menu.getSlot(1).getItem());
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(p);
        // reopen: counts come back from the component
        AddonMenu again = AddonMenu.create(2, p.getInventory(), InteractionHand.MAIN_HAND, radar);
        h.assertTrue(again.getSlot(0).getItem().is(battery()) && again.getSlot(0).getItem().getCount() == 8, "reopened: "
                + again.getSlot(0).getItem());
        // taking batteries out clamps the energy
        RadarItem.setEnergy(radar, Integer.MAX_VALUE);
        again.clicked(0, 0, ClickType.PICKUP, p);
        h.assertTrue(again.getCarried().getCount() == 8 && AddonRules.count(radar, AddonRegistry.BATTERY) == 0, "battery removal");
        h.assertTrue(RadarItem.energy(radar) == SignalRadarConfig.capacity(), "energy clamped after removal: " + RadarItem.energy(radar));
        again.setCarried(ItemStack.EMPTY);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void oldAddonsComponentFormatDecodes(GameTestHelper h) {
        ResourceLocation ore = AddonRegistry.ORE;
        ResourceLocation container = AddonRegistry.CONTAINER;
        // JSON: the old plain id list
        List<AddonEntry> old = AddonEntry.LIST_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("[\"signalradar:addon_ore\", \"signalradar:addon_container\"]")).getOrThrow();
        h.assertTrue(old.equals(List.of(AddonEntry.of(ore), AddonEntry.of(container))), "old json " + old);
        // JSON: new mixed form
        List<AddonEntry> mixed = AddonEntry.LIST_CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("[\"signalradar:addon_ore\", {\"id\": \"signalradar:addon_battery\", \"count\": 5}]")).getOrThrow();
        h.assertTrue(mixed.equals(List.of(AddonEntry.of(ore), new AddonEntry(AddonRegistry.BATTERY, 5))), "mixed json " + mixed);
        // NBT: the old list of string tags
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf("signalradar:addon_ore"));
        tags.add(StringTag.valueOf("signalradar:addon_container"));
        List<AddonEntry> oldNbt = AddonEntry.LIST_CODEC.parse(NbtOps.INSTANCE, tags).getOrThrow();
        h.assertTrue(oldNbt.equals(old), "old nbt " + oldNbt);
        // count 1 still writes the old format; a mixed list round trips through NBT
        Tag written = AddonEntry.LIST_CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
        h.assertTrue(written.equals(tags), "count 1 writes plain ids: " + written);
        Tag mixedTag = AddonEntry.LIST_CODEC.encodeStart(NbtOps.INSTANCE, mixed).getOrThrow();
        h.assertTrue(AddonEntry.LIST_CODEC.parse(NbtOps.INSTANCE, mixedTag).getOrThrow().equals(mixed), "mixed nbt " + mixedTag);
        // whole stack round trip with the registry context
        ItemStack radar = radar(2);
        RadarItem.setAddonEntries(radar, mixed);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, radar).getOrThrow();
        ItemStack back = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        h.assertTrue(RadarItem.addonEntries(back).equals(mixed) && RadarItem.addons(back).equals(List.of(ore, AddonRegistry.BATTERY)),
                "stack round trip " + RadarItem.addonEntries(back));
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void slotCountFollowsConfig(GameTestHelper h) {
        try {
            SignalRadarConfig.overrideSlotsByTier(new int[] {2, 0, 3, 5, 9});
            h.assertTrue(RadarItem.slots(0) == 2 && RadarItem.slots(1) == 1 && RadarItem.slots(2) == 3 && RadarItem.slots(3) == 5
                    && RadarItem.slots(4) == 5, "slots follow the config clamped to 1..5");
            ServerPlayer p = player(h);
            ItemStack radar = radar(0);
            p.setItemInHand(InteractionHand.MAIN_HAND, radar);
            AddonMenu menu = AddonMenu.create(1, p.getInventory(), InteractionHand.MAIN_HAND, radar);
            h.assertTrue(menu.slotCount() == 2 && !(menu.getSlot(1) instanceof AddonMenu.LockedSlot)
                    && menu.getSlot(2) instanceof AddonMenu.LockedSlot, "menu slots at tier 0 = 2");
            RadarItem.setAddons(radar, List.of(AddonRegistry.CONTAINER, AddonRegistry.BATTERY));
            h.assertTrue(AddonRules.batteries(radar) == 1, "battery in the configured second slot counts");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } finally {
            SignalRadarConfig.overrideSlotsByTier(null);
        }
        h.assertTrue(RadarItem.slots(0) == 1, "override cleared");
        h.succeed();
    }
}
