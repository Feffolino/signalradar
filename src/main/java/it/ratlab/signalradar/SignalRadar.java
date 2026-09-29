// SPDX-License-Identifier: MIT
package it.ratlab.signalradar;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SignalRadar.MOD_ID)
public final class SignalRadar {
    public static final String MOD_ID = "signalradar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SignalRadar(IEventBus modBus, ModContainer container) {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
