// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SignalRadar.MOD_ID);

    /** The radar's addon slots (opened with sneak + use). */
    public static final Supplier<MenuType<AddonMenu>> ADDONS = MENUS.register("addons",
            () -> IMenuTypeExtension.create(AddonMenu::new));

    private ModMenus() {}
}
