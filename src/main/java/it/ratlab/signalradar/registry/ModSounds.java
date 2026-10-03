// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sound events; {@code assets/signalradar/sounds.json} maps them to the synthesized OGGs. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SignalRadar.MOD_ID);

    /** A new scan arrived while the radar is held. */
    public static final RegistryObject<SoundEvent> SCAN_PING = register("scan_ping");
    /** The sweep crossed a narrative or motion tracker blip. */
    public static final RegistryObject<SoundEvent> BLIP = register("blip");
    /** A target was found. */
    public static final RegistryObject<SoundEvent> TARGET_FOUND = register("target_found");
    /** Motion tracker beep. */
    public static final RegistryObject<SoundEvent> MOTION_BEEP = register("motion_beep");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(SignalRadar.id(name)));
    }
}
