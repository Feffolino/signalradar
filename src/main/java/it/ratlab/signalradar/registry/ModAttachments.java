// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.progress.PlayerData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Data attachments. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SignalRadar.MOD_ID);

    /** Unlocked / found targets and the last death of a player. Persisted, copied on death, not synced to the client. */
    public static final Supplier<AttachmentType<PlayerData>> PLAYER_DATA = ATTACHMENTS.register("player_data",
            () -> AttachmentType.builder(() -> PlayerData.EMPTY).serialize(PlayerData.CODEC).copyOnDeath().build());

    private ModAttachments() {}
}
