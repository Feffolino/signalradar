// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.item;

import it.ratlab.signalradar.SignalRadarConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Receive-only FE view over the radar's energy component. The radar spends energy internally, never exports it. */
public final class RadarEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;

    public RadarEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive() || maxReceive <= 0) {
            return 0;
        }
        int stored = RadarItem.energy(stack);
        int accepted = Math.min(Math.min(maxReceive, SignalRadarConfig.maxReceive()), SignalRadarConfig.capacity() - stored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            RadarItem.setEnergy(stack, stored + accepted);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return RadarItem.energy(stack);
    }

    @Override
    public int getMaxEnergyStored() {
        return SignalRadarConfig.capacity();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return stack.getCount() == 1;
    }
}
