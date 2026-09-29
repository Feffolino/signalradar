// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.registry;

import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.item.RadarModuleItem;
import java.util.List;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SignalRadar.MOD_ID);

    public static final DeferredItem<RadarItem> RADAR = ITEMS.register("radar",
            () -> new RadarItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<RadarModuleItem> MODULE_1 = module(1);
    public static final DeferredItem<RadarModuleItem> MODULE_2 = module(2);
    public static final DeferredItem<RadarModuleItem> MODULE_3 = module(3);
    public static final DeferredItem<RadarModuleItem> MODULE_4 = module(4);

    public static final List<DeferredItem<RadarModuleItem>> MODULES = List.of(MODULE_1, MODULE_2, MODULE_3, MODULE_4);

    private static DeferredItem<RadarModuleItem> module(int tier) {
        return ITEMS.register("radar_module_" + tier, () -> new RadarModuleItem(tier, new Item.Properties().stacksTo(16)));
    }

    private ModItems() {}
}
