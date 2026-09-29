// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Sound events; {@code assets/signalradar/sounds.json} maps them to vanilla placeholder sounds. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SignalRadar.MOD_ID);

    /** A new scan arrived while the radar is held. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN_PING = register("scan_ping");
    /** The sweep crossed a narrative or motion tracker blip. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BLIP = register("blip");
    /** A target was found (phase 5). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TARGET_FOUND = register("target_found");
    /** Motion tracker beep. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTION_BEEP = register("motion_beep");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SignalRadar.id(name)));
    }
}
