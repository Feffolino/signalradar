// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.scan.BlockLocatorScan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 6: compat addons. Without their mods the addons must simply not exist and nothing may touch mod classes; with the
 * mods on the runtime classpath ({@code runGameTestServerCompat}) every compat detector is exercised. The bodies that use
 * optional-mod classes live in {@code test.compat} and are only reached after a {@code ModList} check.
 */
@GameTestHolder(SignalRadar.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("removal")
public final class CompatGameTests {
    private static final String EMPTY = "gametest_empty";

    private CompatGameTests() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(CompatGameTests.class));
    }

    public static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(0, 1, 0))));
        return p;
    }

    public static List<Hit> run(ResourceLocation id, ServerPlayer p, GameTestHelper h, int radius) {
        return Detectors.run(AddonSettings.defaults(AddonRegistry.get(id).orElseThrow()), p, h.getLevel(), radius,
                BlockLocatorScan.Budget.unlimited(), 100);
    }

    private static boolean loaded(String mod) {
        return ModList.get().isLoaded(mod);
    }

    // ------------------------------------------------------------------ without the mods

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void compatAddonsFollowTheirMods(GameTestHelper h) {
        Object[][] rows = {
                {AddonRegistry.MANHOLE, "manholes", 0, 96, 160, 5, 5, "manhole"},
                {AddonRegistry.LOOTR, "lootr", 2, 48, 96, 10, 10, "lootr"},
                {AddonRegistry.TEAM, "ftbteams", 1, 100_000, 100_000, 1, 5, "team"},
        };
        ServerPlayer p = player(h);
        for (Object[] row : rows) {
            ResourceLocation id = (ResourceLocation) row[0];
            var def = AddonRegistry.get(id).orElseThrow(() -> new IllegalStateException("compat definition missing: " + id));
            boolean present = loaded((String) row[1]);
            h.assertTrue(def.requiredModId().equals(row[1]), id + " required mod");
            h.assertTrue(AddonRegistry.isActive(def) == present, id + " active flag");
            h.assertTrue(AddonRegistry.item(id).isPresent() == present, id + " item registered = " + present);
            AddonSettings s = AddonSettings.defaults(def);
            h.assertTrue(s.minTier() == (int) row[2] && s.radiusMin() == (int) row[3] && s.radiusMax() == (int) row[4]
                    && s.refreshSeconds() == (int) row[5] && s.energyCost() == (int) row[6] && def.category().equals(row[7]),
                    id + " defaults " + s);
            // a scan of an addon whose mod is missing must be empty and must not touch any mod class
            if (!present) {
                h.assertTrue(run(id, p, h, 64).isEmpty(), id + " produced hits without its mod");
            }
        }
        h.assertTrue(AddonRegistry.active().stream().noneMatch(d -> d.requiredModId() != null && !loaded(d.requiredModId())),
                "active() lists an addon of a missing mod");
        h.succeed();
    }

    // ------------------------------------------------------------------ with the mods (runGameTestServerCompat)

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void manholeAddonListsOnlyUnopenedNetworkNodes(GameTestHelper h) {
        if (loaded("manholes")) {
            SignalRadar.LOGGER.info("compat check running: Manhole");
            it.ratlab.signalradar.test.compat.ManholeChecks.run(h);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void lootAddonListsOnlyUnopenedLootrContainers(GameTestHelper h) {
        if (loaded("lootr")) {
            SignalRadar.LOGGER.info("compat check running: Lootr");
            it.ratlab.signalradar.test.compat.LootrChecks.run(h);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void lootrOpenInvalidatesLootCache(GameTestHelper h) {
        if (loaded("lootr")) {
            it.ratlab.signalradar.test.compat.LootrChecks.cache(h);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void containerAddonFollowsIncludeLootrConfig(GameTestHelper h) {
        if (loaded("lootr")) {
            it.ratlab.signalradar.test.compat.LootrChecks.includeConfig(h);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void teamAddonListsOnlineTeammates(GameTestHelper h) {
        if (loaded("ftbteams")) {
            try {
                SignalRadar.LOGGER.info("compat check running: Team");
                it.ratlab.signalradar.test.compat.TeamChecks.run(h);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException("team setup failed: " + e.getMessage(), e);
            }
        }
        h.succeed();
    }

    /** Default recipes follow the startup config; compat addon recipes exist only when their mod is loaded. */
    @GameTest(templateNamespace = SignalRadar.MOD_ID, template = EMPTY)
    public static void defaultRecipesFollowConfigAndMods(GameTestHelper h) {
        var cond = it.ratlab.signalradar.recipe.DefaultRecipesCondition.INSTANCE;
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("type", "signalradar:default_recipes_enabled");
        h.assertTrue(net.minecraftforge.common.crafting.CraftingHelper.getCondition(json) instanceof it.ratlab.signalradar.recipe.DefaultRecipesCondition,
                "condition serializer not registered");
        boolean enabled = it.ratlab.signalradar.SignalRadarStartupConfig.defaultRecipesEnabled();
        h.assertTrue(cond.test(net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY) == enabled, "condition differs from the config value");
        var rm = h.getLevel().getServer().getRecipeManager();
        String[] base = {"radar", "radar_module_1", "radar_module_2", "radar_module_3", "radar_module_4",
                "addon_container", "addon_ore", "addon_biosign", "addon_structure", "addon_motion"};
        for (String n : base) {
            h.assertTrue(rm.byKey(SignalRadar.id("default/" + n)).isPresent() == enabled, "default/" + n + " loaded == " + enabled);
        }
        String[][] compat = {{"addon_manhole", "manholes"}, {"addon_lootr", "lootr"}, {"addon_team", "ftbteams"}};
        for (String[] c : compat) {
            h.assertTrue(rm.byKey(SignalRadar.id("default/" + c[0])).isPresent() == (enabled && loaded(c[1])),
                    "default/" + c[0] + " loaded == enabled && " + c[1] + " loaded");
        }
        h.assertTrue(rm.byKey(SignalRadar.id("radar_upgrade")).isPresent(), "radar_upgrade must stay outside default/");
        SignalRadar.LOGGER.info("default recipes check: enabled={} manholes={} lootr={} ftbteams={}", enabled,
                loaded("manholes"), loaded("lootr"), loaded("ftbteams"));
        h.succeed();
    }
}
