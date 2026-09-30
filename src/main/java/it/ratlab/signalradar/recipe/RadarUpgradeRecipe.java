// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.recipe;

import it.ratlab.signalradar.api.RadarUpgradedEvent;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.item.RadarModuleItem;
import it.ratlab.signalradar.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/** Shapeless: exactly one radar + exactly one module N, radar tier must be N-1. Output keeps every component. */
public class RadarUpgradeRecipe extends CustomRecipe {
    public RadarUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ItemStack radar, int moduleTier) {}

    @Nullable
    private static Parts find(CraftingInput input) {
        return find(input.items());
    }

    @Nullable
    private static Parts find(List<ItemStack> items) {
        ItemStack radar = ItemStack.EMPTY;
        int moduleTier = -1;
        for (ItemStack s : items) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof RadarItem) {
                if (!radar.isEmpty()) {
                    return null;
                }
                radar = s;
            } else if (s.getItem() instanceof RadarModuleItem module) {
                if (moduleTier != -1) {
                    return null;
                }
                moduleTier = module.tier();
            } else {
                return null;
            }
        }
        return radar.isEmpty() || moduleTier < 0 ? null : new Parts(radar, moduleTier);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Parts p = find(input);
        return p != null && RadarItem.tier(p.radar()) == p.moduleTier() - 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parts p = find(input);
        if (p == null || RadarItem.tier(p.radar()) != p.moduleTier() - 1) {
            return ItemStack.EMPTY;
        }
        ItemStack out = p.radar().copyWithCount(1);
        RadarItem.setTier(out, p.moduleTier());
        return out;
    }

    /**
     * Tier of the radar in a crafting grid that is a valid upgrade input, or -1. Used from
     * {@link PlayerEvent.ItemCraftedEvent}, which is posted while the ingredients are still in the grid.
     */
    public static int upgradeFromTier(Container grid) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            items.add(grid.getItem(i));
        }
        Parts p = find(items);
        return p != null && RadarItem.tier(p.radar()) == p.moduleTier() - 1 ? RadarItem.tier(p.radar()) : -1;
    }

    /** NeoForge bus: posts {@link RadarUpgradedEvent} when a player takes an upgraded radar out of the grid. */
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getCrafting().getItem() instanceof RadarItem)) {
            return;
        }
        int oldTier = upgradeFromTier(event.getInventory());
        int newTier = RadarItem.tier(event.getCrafting());
        if (oldTier >= 0 && newTier == oldTier + 1) {
            NeoForge.EVENT_BUS.post(new RadarUpgradedEvent(player, event.getCrafting(), oldTier, newTier));
        }
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RADAR_UPGRADE.get();
    }
}
