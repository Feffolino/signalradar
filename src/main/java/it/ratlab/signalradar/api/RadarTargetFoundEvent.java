// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.api;

import it.ratlab.signalradar.target.TargetDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Posted on the Forge event bus (server side) when a player finds a narrative target: the found flag and the stage
 * are already set, the sound and action bar message follow. Fired once per player and target until
 * {@code resetfound}. Not cancellable.
 */
public class RadarTargetFoundEvent extends PlayerEvent {
    private final ResourceLocation targetId;
    private final TargetDef target;

    public RadarTargetFoundEvent(ServerPlayer player, ResourceLocation targetId, TargetDef target) {
        super(player);
        this.targetId = targetId;
        this.target = target;
    }

    public ServerPlayer getPlayer() {
        return (ServerPlayer) getEntity();
    }

    public ResourceLocation getTargetId() {
        return targetId;
    }

    public TargetDef getTarget() {
        return target;
    }
}
