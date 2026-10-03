// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.test.compat;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.detect.Hit;
import it.ratlab.signalradar.test.CompatGameTests;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** Team addon checks; only loaded when FTB Teams is present. */
public final class TeamChecks {
    private TeamChecks() {}

    private static boolean has(List<Hit> hits, ServerPlayer p) {
        return hits.stream().anyMatch(x -> x.key().equals(p.getUUID().toString()));
    }

    public static void run(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        h.assertTrue(FTBTeamsAPI.api().isManagerLoaded(), "FTB Teams manager not loaded");
        ServerPlayer a = CompatGameTests.player(h);
        ServerPlayer mate = CompatGameTests.player(h);
        ServerPlayer stranger = CompatGameTests.player(h);
        mate.setPos(mate.getX() + 500, mate.getY(), mate.getZ());
        Team party = FTBTeamsAPI.api().getManager().createPartyTeam(a, "sr_test_" + Integer.toHexString(a.getUUID().hashCode()), "",
                Color4I.WHITE);
        try {
            // nobody else in the team yet
            h.assertTrue(CompatGameTests.run(AddonRegistry.TEAM, a, h, 100_000).isEmpty(), "teammates before anyone joined");
            PartyTeam pt = (PartyTeam) party;
            pt.invite(a, List.of(new GameProfile(mate.getUUID(), mate.getGameProfile().getName())));
            pt.join(mate);
            List<Hit> hits = CompatGameTests.run(AddonRegistry.TEAM, a, h, 100_000);
            h.assertTrue(has(hits, mate), "teammate missing");
            h.assertTrue(!has(hits, a), "self listed");
            h.assertTrue(!has(hits, stranger), "non-member listed");
            h.assertTrue(hits.get(0).name().getString().equals(mate.getGameProfile().getName()), "blip name");
            h.assertTrue(hits.get(0).icon().equals("player:" + mate.getUUID()), "team icon " + hits.get(0).icon());
            h.assertTrue(!has(CompatGameTests.run(AddonRegistry.TEAM, a, h, 100), mate), "radius ignored");
        } finally {
            try {
                ((PartyTeam) party).forceDisband(h.getLevel().getServer().createCommandSourceStack());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException ignored) {
                // best effort cleanup of the test party
            }
        }
    }
}
