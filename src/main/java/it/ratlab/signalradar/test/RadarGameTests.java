// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.SignalRadarConfig;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
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
        h.assertTrue(got == SignalRadarConfig.maxReceive(), "received " + got + ", expected " + SignalRadarConfig.maxReceive());
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
}
