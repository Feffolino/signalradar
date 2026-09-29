// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import com.mojang.serialization.Codec;
import it.ratlab.signalradar.SignalRadar;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, SignalRadar.MOD_ID);

    /** Radar tier 0..4. Absent = 0 (never set as an item default: components register after items). */
    public static final Supplier<DataComponentType<Integer>> TIER = COMPONENTS.registerComponentType("tier",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Stored FE. Absent = 0. */
    public static final Supplier<DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
