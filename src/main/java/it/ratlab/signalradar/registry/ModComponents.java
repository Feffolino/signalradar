// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import com.mojang.serialization.Codec;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.AddonEntry;
import java.util.List;
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

    /**
     * Installed addons (id + count), slot order, no gaps, each id once (immutable list: value equality, no aliasing between
     * stack copies). Absent = none. Reads the old plain id list as count 1 (see {@link AddonEntry}).
     */
    public static final Supplier<DataComponentType<List<AddonEntry>>> ADDONS = COMPONENTS.registerComponentType("addons",
            b -> b.persistent(AddonEntry.LIST_CODEC)
                    .networkSynchronized(AddonEntry.STREAM_CODEC.apply(ByteBufCodecs.list(16))));

    private ModComponents() {}
}
