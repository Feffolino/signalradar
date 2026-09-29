// SPDX-License-Identifier: MIT
package it.ratlab.signalradar.addon.menu;

import it.ratlab.signalradar.addon.AddonItem;
import it.ratlab.signalradar.addon.AddonRegistry;
import it.ratlab.signalradar.addon.AddonRules;
import it.ratlab.signalradar.addon.AddonSettings;
import it.ratlab.signalradar.item.RadarItem;
import it.ratlab.signalradar.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The radar's addon slots. While the menu is open the container is the single source of truth and every change is
 * written to the radar in the opening hand. That radar is identified by stack identity: if it is no longer the item in
 * that hand, clicks are ignored and the menu closes (addons are already stored in the radar's component, so nothing
 * is returned, nothing is duplicated). The radar's own inventory slot cannot be picked up and swapping it out is ignored.
 */
public class AddonMenu extends AbstractContainerMenu {
    public static final int MAX_SLOTS = 5;
    public static final int ADDON_X = 44;
    public static final int ADDON_Y = 20;
    public static final int INV_Y = 51;
    public static final int HOTBAR_Y = 109;

    private final Player player;
    private final InteractionHand hand;
    private final int tier;
    private final int slotCount;
    private final SimpleContainer container = new SimpleContainer(MAX_SLOTS);
    /** Server only: the radar stack the menu was opened for. */
    @Nullable
    private final ItemStack radarRef;
    /** Installed ids beyond the tier's slots (config lowered): kept untouched. */
    private final List<ResourceLocation> overflow = new ArrayList<>();
    private final int radarMenuSlot;

    /** Client side (from the open packet): hand, tier, slot count. */
    public AddonMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, buf.readVarInt(), buf.readVarInt(), null);
    }

    /** Server side: a menu for the radar currently in {@code hand} of the inventory's player. */
    public static AddonMenu create(int id, Inventory inv, InteractionHand hand, ItemStack radar) {
        return new AddonMenu(id, inv, hand, RadarItem.tier(radar), RadarItem.slots(RadarItem.tier(radar)), radar);
    }

    private AddonMenu(int id, Inventory inv, InteractionHand hand, int tier, int slotsArg, @Nullable ItemStack radar) {
        super(ModMenus.ADDONS.get(), id);
        this.player = inv.player;
        this.hand = hand;
        this.tier = tier;
        this.slotCount = Math.min(slotsArg, MAX_SLOTS);
        this.radarRef = radar;
        for (int i = 0; i < MAX_SLOTS; i++) {
            int x = ADDON_X + i * 18;
            addSlot(i < slotCount ? new AddonSlot(container, i, x, ADDON_Y) : new LockedSlot(container, i, x, ADDON_Y));
        }
        int locked = -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            boolean radarSlot = hand == InteractionHand.MAIN_HAND && col == inv.selected;
            Slot s = radarSlot ? new LockedSlot(inv, col, 8 + col * 18, HOTBAR_Y) : new Slot(inv, col, 8 + col * 18, HOTBAR_Y);
            if (radarSlot) {
                locked = slots.size();
            }
            addSlot(s);
        }
        this.radarMenuSlot = locked;
        if (radar != null) {
            List<ResourceLocation> installed = AddonRules.installed(radar);
            for (int i = 0; i < installed.size(); i++) {
                if (i < slotCount) {
                    AddonRegistry.item(installed.get(i)).ifPresent(item -> {
                        for (int k = 0; k < slotCount; k++) {
                            if (container.getItem(k).isEmpty()) {
                                container.setItem(k, new ItemStack(item));
                                return;
                            }
                        }
                    });
                } else {
                    overflow.add(installed.get(i));
                }
            }
            container.addListener(c -> writeBack());
        }
    }

    /** Opens the menu for the radar in {@code hand}. Server only. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        ItemStack radar = player.getItemInHand(hand);
        if (!(radar.getItem() instanceof RadarItem)) {
            return;
        }
        int tier = RadarItem.tier(radar);
        int slots = Math.min(RadarItem.slots(tier), MAX_SLOTS);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> AddonMenu.create(id, inv, hand, radar),
                Component.translatable("gui.signalradar.addons.title")), buf -> {
                    buf.writeBoolean(hand == InteractionHand.OFF_HAND);
                    buf.writeVarInt(tier);
                    buf.writeVarInt(slots);
                });
    }

    public int slotCount() {
        return slotCount;
    }

    public int tier() {
        return tier;
    }

    public SimpleContainer addonContainer() {
        return container;
    }

    @Nullable
    private ItemStack radarIfValid() {
        if (radarRef == null) {
            return null;
        }
        ItemStack current = player.getItemInHand(hand);
        return current == radarRef && !current.isEmpty() && current.getItem() instanceof RadarItem ? current : null;
    }

    /** Server: container -> radar component (compact, plus untouched overflow). */
    private void writeBack() {
        ItemStack radar = radarIfValid();
        if (radar == null) {
            return;
        }
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof AddonItem a && !ids.contains(a.defId())) {
                ids.add(a.defId());
            }
        }
        for (ResourceLocation o : overflow) {
            if (!ids.contains(o)) {
                ids.add(o);
            }
        }
        RadarItem.setAddons(radar, ids);
    }

    /** Why {@code stack} cannot go into addon slot {@code index}; empty when it fits. */
    public Optional<Component> refusal(ItemStack stack, int index) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        if (index >= slotCount) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.locked"));
        }
        if (!(stack.getItem() instanceof AddonItem item)) {
            return Optional.of(Component.translatable("gui.signalradar.addons.refusal.not_addon"));
        }
        Optional<Component> r = AddonRegistry.get(item.defId())
                .map(d -> AddonRules.refusal(AddonSettings.of(d), tier))
                .orElse(Optional.of(Component.translatable("gui.signalradar.addons.refusal.unavailable")));
        if (r.isPresent()) {
            return r;
        }
        for (int i = 0; i < slotCount; i++) {
            if (i != index && container.getItem(i).getItem() == stack.getItem()) {
                return Optional.of(Component.translatable("gui.signalradar.addons.refusal.duplicate"));
            }
        }
        return Optional.empty();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player p) {
        boolean server = !p.level().isClientSide;
        if (server) {
            if (radarIfValid() == null) {
                p.closeContainer();
                return;
            }
            if (clickType == ClickType.SWAP && ((hand == InteractionHand.OFF_HAND && button == 40)
                    || (hand == InteractionHand.MAIN_HAND && button == p.getInventory().selected))) {
                return; // would move the radar out of its hand
            }
        }
        super.clicked(slotId, button, clickType, p);
        if (server && radarIfValid() == null) {
            p.closeContainer();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index == radarMenuSlot || index < 0 || index >= slots.size() || (radarRef != null && radarIfValid() == null)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < MAX_SLOTS) {
            if (!moveItemStackTo(stack, MAX_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, MAX_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return radarRef == null || radarIfValid() != null;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (!p.level().isClientSide) {
            writeBack();
        }
    }

    /** An addon slot: accepts one allowed addon each. */
    private final class AddonSlot extends Slot {
        private final int index;

        AddonSlot(SimpleContainer c, int index, int x, int y) {
            super(c, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && refusal(stack, index).isEmpty();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** A slot the player can neither fill nor empty (locked addon slots, the radar's own hotbar slot). */
    public static final class LockedSlot extends Slot {
        public LockedSlot(net.minecraft.world.Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player p) {
            return false;
        }
    }
}
