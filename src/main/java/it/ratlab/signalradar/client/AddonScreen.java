// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.client;

import com.mojang.blaze3d.systems.RenderSystem;
import it.ratlab.signalradar.SignalRadar;
import it.ratlab.signalradar.addon.menu.AddonMenu;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Addon slot screen: locked slots are crossed out, a refused carried addon shows its reason as tooltip. */
public class AddonScreen extends AbstractContainerScreen<AddonMenu> {
    private static final ResourceLocation TEXTURE = SignalRadar.id("textures/gui/addon_slots.png");
    /** Real size of {@code addon_slots.png} (the 176x133 background sits in the top left corner). */
    private static final int TEX_W = 256;
    private static final int TEX_H = 256;

    public AddonScreen(AddonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 133;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /**
     * Screen.render draws the background (dim, blur), then the container; vanilla container screens finish with the
     * tooltip pass, which {@link AbstractContainerScreen} itself does not do.
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    /**
     * Called from {@code renderBackground}, after the blur and the menu dim. {@code blit} reads the global shader colour,
     * blend and depth state; whatever drew before (item models, tooltips, HUD overlays of other mods) may have left them
     * changed, which showed as a missing or garbled background. Reset them explicitly, flush pending GUI batches first and
     * pass the sheet size instead of relying on the implicit 256x256 overload.
     */
    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.flush();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        g.blit(TEXTURE, leftPos, topPos, 0, 0f, 0f, imageWidth, imageHeight, TEX_W, TEX_H);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        super.renderSlot(g, slot);
        if (slot instanceof AddonMenu.LockedSlot && slot.index < AddonMenu.MAX_SLOTS) {
            int x = slot.x;
            int y = slot.y;
            g.fill(x, y, x + 16, y + 16, 0xB0181818);
            for (int i = 0; i < 16; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFC03030);
                g.fill(x + 15 - i, y + i, x + 16 - i, y + i + 1, 0xFFC03030);
            }
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.index < AddonMenu.MAX_SLOTS && !hovered.hasItem()) {
            ItemStack carried = menu.getCarried();
            Optional<Component> reason = carried.isEmpty() ? Optional.empty() : menu.refusal(carried, hovered.index);
            if (reason.isPresent()) {
                g.renderTooltip(font, reason.get(), mouseX, mouseY);
                return;
            }
            if (carried.isEmpty() && hovered.index >= menu.slotCount()) {
                g.renderTooltip(font, Component.translatable("gui.signalradar.addons.locked_slot"), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
