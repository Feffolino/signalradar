// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.recipe;

import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.item.RadarModuleItem;
import it.ratlab.signalradar.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Shapeless: exactly one radar + exactly one module N, radar tier must be N-1. Output keeps every component. */
public class RadarUpgradeRecipe extends CustomRecipe {
    public RadarUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ItemStack radar, int moduleTier) {}

    @Nullable
    private static Parts find(CraftingInput input) {
        ItemStack radar = ItemStack.EMPTY;
        int moduleTier = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
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

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.RADAR_UPGRADE.get();
    }
}
