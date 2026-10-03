// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RadarEnergyCapabilityProvider implements ICapabilityProvider {
    private final ItemStack stack;
    private final LazyOptional<IEnergyStorage> energy;

    public RadarEnergyCapabilityProvider(ItemStack stack) {
        this.stack = stack;
        this.energy = LazyOptional.of(() -> new RadarEnergyStorage(this.stack));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energy.cast();
        }
        return LazyOptional.empty();
    }
}
