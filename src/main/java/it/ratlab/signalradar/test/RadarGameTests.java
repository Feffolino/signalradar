// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.recipe.RadarUpgradeRecipe;
import it.ratlab.signalradar.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless checks run by {@code ./gradlew runGameTestServer} (registered only with -Dsignalradar.gametests=true). */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadarGameTests {
    private static final String EMPTY = "gametest_empty";

    private RadarGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(RadarGameTests.class));
    }

    private static IEnergyStorage energyOf(GameTestHelper h, ItemStack stack) {
        IEnergyStorage e = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        h.assertTrue(e != null, "radar has no FE capability");
        return e;
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void receiveIsCappedByMaxReceive(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        int got = energyOf(h, radar).receiveEnergy(1_000_000, false);
        int expected = Math.min(1_000_000, Math.min(SignalRadarConfig.maxReceive(), RadarItem.capacity(radar)));
        h.assertTrue(got == expected, "received " + got + ", expected " + expected);
        h.assertTrue(RadarItem.energy(radar) == got, "stored " + RadarItem.energy(radar));
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void receiveIsCappedByCapacity(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        int cap = SignalRadarConfig.capacity();
        RadarItem.setEnergy(radar, cap - 30);
        int got = energyOf(h, radar).receiveEnergy(1_000_000, false);
        h.assertTrue(got == 30, "received " + got + ", expected 30");
        h.assertTrue(RadarItem.energy(radar) == cap, "not full: " + RadarItem.energy(radar));
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void simulateDoesNotStore(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        energyOf(h, radar).receiveEnergy(50, true);
        h.assertTrue(RadarItem.energy(radar) == 0, "simulate stored energy");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void extractIsRefused(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        RadarItem.setEnergy(radar, 500);
        IEnergyStorage e = energyOf(h, radar);
        h.assertTrue(!e.canExtract() && e.extractEnergy(100, false) == 0, "extraction allowed");
        h.assertTrue(RadarItem.energy(radar) == 500, "energy changed");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void tierIsClamped(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        h.assertTrue(RadarItem.tier(radar) == 0, "default tier not 0");
        RadarItem.setTier(radar, 9);
        h.assertTrue(RadarItem.tier(radar) == RadarItem.MAX_TIER, "tier not clamped: " + RadarItem.tier(radar));
        h.succeed();
    }

    private static CraftingInput grid(ItemStack... stacks) {
        List<ItemStack> items = new ArrayList<>(List.of(stacks));
        while (items.size() < 9) {
            items.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, items);
    }

    private static final RadarUpgradeRecipe UPGRADE = new RadarUpgradeRecipe(CraftingBookCategory.EQUIPMENT);

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradeRaisesTierAndKeepsEnergy(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        RadarItem.setEnergy(radar, 1234);
        CraftingInput in = grid(radar, new ItemStack(ModItems.MODULE_1.get()));
        h.assertTrue(UPGRADE.matches(in, h.getLevel()), "tier 0 + module 1 should match");
        ItemStack out = UPGRADE.assemble(in, h.getLevel().registryAccess());
        h.assertTrue(RadarItem.tier(out) == 1, "tier " + RadarItem.tier(out));
        h.assertTrue(RadarItem.energy(out) == 1234, "energy " + RadarItem.energy(out));
        h.assertTrue(out.getCount() == 1, "count " + out.getCount());
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradeRefusesSkippedTier(GameTestHelper h) {
        CraftingInput in = grid(new ItemStack(ModItems.RADAR.get()), new ItemStack(ModItems.MODULE_2.get()));
        h.assertTrue(!UPGRADE.matches(in, h.getLevel()), "tier 0 + module 2 must not match");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradeRefusesSameTier(GameTestHelper h) {
        ItemStack radar = new ItemStack(ModItems.RADAR.get());
        RadarItem.setTier(radar, 1);
        CraftingInput in = grid(radar, new ItemStack(ModItems.MODULE_1.get()));
        h.assertTrue(!UPGRADE.matches(in, h.getLevel()), "tier 1 + module 1 must not match");
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void upgradeRefusesExtraItems(GameTestHelper h) {
        CraftingInput twoModules = grid(new ItemStack(ModItems.RADAR.get()),
                new ItemStack(ModItems.MODULE_1.get()), new ItemStack(ModItems.MODULE_1.get()));
        CraftingInput twoRadars = grid(new ItemStack(ModItems.RADAR.get()), new ItemStack(ModItems.RADAR.get()),
                new ItemStack(ModItems.MODULE_1.get()));
        CraftingInput alone = grid(new ItemStack(ModItems.RADAR.get()));
        h.assertTrue(!UPGRADE.matches(twoModules, h.getLevel()), "two modules matched");
        h.assertTrue(!UPGRADE.matches(twoRadars, h.getLevel()), "two radars matched");
        h.assertTrue(!UPGRADE.matches(alone, h.getLevel()), "radar alone matched");
        h.succeed();
    }
}
