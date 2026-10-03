// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SignalRadar.MOD_ID);

    /** The radar's addon slots (opened with sneak + use). */
    public static final RegistryObject<MenuType<AddonMenu>> ADDONS = MENUS.register("addons",
            () -> IForgeMenuType.create(AddonMenu::new));

    private ModMenus() {}
}
