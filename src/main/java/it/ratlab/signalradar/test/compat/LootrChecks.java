// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test.compat;

import it.ratlab.signalradar.addon.AddonConfig;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.AddonCache;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.compat.lootr.LootrEvents;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import net.minecraft.world.phys.Vec3;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.test.CompatGameTests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity;

/** Lootr addon checks; only loaded when Lootr is present. */
public final class LootrChecks {
    private LootrChecks() {}

    private static boolean has(List<Hit> hits, BlockPos abs) {
        String key = abs.getX() + "," + abs.getY() + "," + abs.getZ();
        return hits.stream().anyMatch(x -> x.key().equals(key));
    }

    public static void run(GameTestHelper h) {
        ServerPlayer p = CompatGameTests.player(h);
        Block chest = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_chest"));
        h.assertTrue(chest != Blocks.AIR, "lootr:lootr_chest missing");
        BlockPos rel = new BlockPos(3, 1, 1);
        BlockPos plainRel = new BlockPos(1, 1, 3);
        h.setBlock(rel, chest);
        h.setBlock(plainRel, Blocks.CHEST);
        BlockPos abs = h.absolutePos(rel);
        h.assertTrue(h.getLevel().getBlockEntity(abs) instanceof ILootrBlockEntity, "lootr chest has no ILootrBlockEntity");
        List<Hit> hits = CompatGameTests.run(AddonRegistry.LOOTR, p, h, 96);
        h.assertTrue(has(hits, abs), "unopened lootr chest missing");
        h.assertTrue(hits.stream().filter(x -> x.key().equals(abs.getX() + "," + abs.getY() + "," + abs.getZ())).allMatch(x -> x.icon().startsWith("item:")),
                "lootr icon is not an item");
        h.assertTrue(!has(hits, h.absolutePos(plainRel)), "vanilla chest listed");
        h.assertTrue(CompatGameTests.run(AddonRegistry.LOOTR, p, h, 1).isEmpty(), "radius ignored");
        ((ILootrBlockEntity) h.getLevel().getBlockEntity(abs)).addOpener(p);
        h.assertTrue(!has(CompatGameTests.run(AddonRegistry.LOOTR, p, h, 96), abs), "opened lootr chest still listed");
        // another player has not opened it
        ServerPlayer other = CompatGameTests.player(h);
        h.assertTrue(has(CompatGameTests.run(AddonRegistry.LOOTR, other, h, 96), abs), "chest hidden from a player who did not open it");
    }

    /** Opening a Lootr container drops the player's cached Lootr result (no waiting for the addon refresh). */
    public static void cache(GameTestHelper h) {
        ServerPlayer p = CompatGameTests.player(h);
        Block chest = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_chest"));
        BlockPos rel = new BlockPos(3, 1, 1);
        h.setBlock(rel, chest);
        BlockPos abs = h.absolutePos(rel);
        var addon = AddonSettings.defaults(AddonRegistry.get(AddonRegistry.LOOTR).orElseThrow());
        AddonCache cache = AddonCache.INSTANCE;
        java.util.function.Supplier<List<Hit>> detect = () -> Detectors.run(addon, p, h.getLevel(), 96, BlockLocatorScan.Budget.unlimited(), 100);
        Vec3 pos = p.position();
        String dim = h.getLevel().dimension().location().toString();
        List<Hit> first = cache.get(p.getUUID(), AddonRegistry.LOOTR, 100, 200, 96, dim, pos, detect, () -> false);
        h.assertTrue(has(first, abs), "unopened chest not detected");
        ((ILootrBlockEntity) h.getLevel().getBlockEntity(abs)).addOpener(p);
        h.assertTrue(has(cache.get(p.getUUID(), AddonRegistry.LOOTR, 101, 200, 96, dim, pos, detect, () -> false), abs),
                "expected the stale cached result before invalidation");
        LootrEvents.refresh(p);
        h.assertTrue(!has(cache.get(p.getUUID(), AddonRegistry.LOOTR, 102, 200, 96, dim, pos, detect, () -> false), abs),
                "opened chest still listed after invalidation");
        cache.forget(p.getUUID());
    }

    /** {@code addons.container.includeLootrContainers}: the container addon lists Lootr chests only when true. */
    public static void includeConfig(GameTestHelper h) {
        ServerPlayer p = CompatGameTests.player(h);
        Block chest = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_chest"));
        BlockPos rel = new BlockPos(3, 1, 1);
        h.setBlock(rel, chest);
        BlockPos abs = h.absolutePos(rel);
        try {
            AddonConfig.overrideIncludeLootrContainers(true);
            h.assertTrue(has(CompatGameTests.run(AddonRegistry.CONTAINER, p, h, 96), abs), "container addon hides Lootr chest with true");
            AddonConfig.overrideIncludeLootrContainers(false);
            h.assertTrue(!has(CompatGameTests.run(AddonRegistry.CONTAINER, p, h, 96), abs), "container addon shows Lootr chest with false");
            h.setBlock(new BlockPos(1, 1, 3), Blocks.CHEST);
            h.assertTrue(has(CompatGameTests.run(AddonRegistry.CONTAINER, p, h, 96), h.absolutePos(new BlockPos(1, 1, 3))),
                    "vanilla chest hidden with false");
        } finally {
            AddonConfig.overrideIncludeLootrContainers(null);
        }
    }
}
