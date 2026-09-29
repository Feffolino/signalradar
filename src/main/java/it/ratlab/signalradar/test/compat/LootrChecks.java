// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test.compat;

import it.ratlab.signalradar.addon.AddonRegistry;
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

/** Loot addon checks; only loaded when Lootr is present. */
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
        List<Hit> hits = CompatGameTests.run(AddonRegistry.LOOT, p, h, 96);
        h.assertTrue(has(hits, abs), "unopened lootr chest missing");
        h.assertTrue(!has(hits, h.absolutePos(plainRel)), "vanilla chest listed");
        h.assertTrue(CompatGameTests.run(AddonRegistry.LOOT, p, h, 1).isEmpty(), "radius ignored");
        ((ILootrBlockEntity) h.getLevel().getBlockEntity(abs)).addOpener(p);
        h.assertTrue(!has(CompatGameTests.run(AddonRegistry.LOOT, p, h, 96), abs), "opened lootr chest still listed");
        // another player has not opened it
        ServerPlayer other = CompatGameTests.player(h);
        h.assertTrue(has(CompatGameTests.run(AddonRegistry.LOOT, other, h, 96), abs), "chest hidden from a player who did not open it");
    }
}
