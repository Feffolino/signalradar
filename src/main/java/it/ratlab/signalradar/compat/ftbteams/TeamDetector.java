// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import it.ratlab.signalradar.addon.detect.Detectors;
import it.ratlab.signalradar.addon.detect.Hit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Online members of the player's FTB team (excluding the player) in the same dimension. Teammates are fuzzed like every
 * other addon blip. Only class-loaded after a {@code ModList} check.
 */
public final class TeamDetector {
    private TeamDetector() {}

    public static List<Hit> run(ServerPlayer player, ServerLevel level, int radius) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        if (!api.isManagerLoaded()) {
            return List.of();
        }
        Optional<Team> team = api.getManager().getTeamForPlayer(player);
        if (team.isEmpty()) {
            return List.of();
        }
        double r2 = (double) radius * radius;
        List<ServerPlayer> members = new ArrayList<>();
        for (ServerPlayer m : team.get().getOnlineMembers()) {
            if (m == player || m.level() != level || m.isSpectator() || !m.isAlive()) {
                continue;
            }
            double dx = m.getX() - player.getX();
            double dz = m.getZ() - player.getZ();
            if (dx * dx + dz * dz <= r2) {
                members.add(m);
            }
        }
        members.sort(Comparator.comparing(ServerPlayer::getUUID));
        List<Hit> hits = new ArrayList<>();
        for (ServerPlayer m : members) {
            if (hits.size() >= Detectors.MAX_HITS) {
                break;
            }
            hits.add(new Hit(m.getUUID().toString(), m.getName(), m.getX(), m.getY(), m.getZ(), 0));
        }
        return hits;
    }
}
