package dev.wp.matter_manipulator.common.compat.ae;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Menu for the uplink's linking GUI: a single slot that binds a dropped-in Matter Manipulator to
 * this uplink (writing the bind into the item's config), plus an Unlink button (menu button 0).
 */
public class UplinkMenu extends AbstractContainerMenu {

    public static final int UNLINK_BUTTON = 0;

    @Nullable
    private final UplinkBlockEntity be;
    private final SimpleContainer linkContainer = new SimpleContainer(1);

    /** Server constructor. */
    public UplinkMenu(int id, Inventory playerInv, UplinkBlockEntity be) {
        super(AEUplinkRegistrar.UPLINK_MENU.get(), id);
        this.be = be;
        addSlots(playerInv);
    }

    /** Client constructor. */
    public UplinkMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        super(AEUplinkRegistrar.UPLINK_MENU.get(), id);
        this.be = null;
        buf.readBlockPos(); // position is unused client-side beyond opening
        addSlots(playerInv);
    }

    private void addSlots(Inventory playerInv) {
        addSlot(new Slot(linkContainer, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ItemMatterManipulator;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setChanged() {
                super.setChanged();
                bindFromSlot();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    /** The manipulator currently in the link slot, or empty. */
    public ItemStack getLinkedStack() {
        return linkContainer.getItem(0);
    }

    /**
     * Binds the manipulator in the link slot to this uplink. Called from the slot's setChanged
     * (the reliable hook for a SimpleContainer); only the server, which has the block entity,
     * performs the write.
     */
    private void bindFromSlot() {
        if (be == null) return;
        ItemStack stack = linkContainer.getItem(0);
        if (stack.getItem() instanceof ItemMatterManipulator) {
            setBinding(stack, new Location(be.getLevel().dimension(), be.getBlockPos()));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == UNLINK_BUTTON && be != null) {
            ItemStack stack = linkContainer.getItem(0);
            if (stack.getItem() instanceof ItemMatterManipulator) {
                setBinding(stack, null);
            }
            return true;
        }
        return false;
    }

    private static void setBinding(ItemStack stack, @Nullable Location loc) {
        var state = MMState.of(stack);
        var cfg = state.mutate();
        cfg.uplink = loc;
        state.saveIfDirty();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int invStart = 1;
            int invEnd = slots.size();
            if (index == 0) {
                if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            } else if (stack.getItem() instanceof ItemMatterManipulator) {
                if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, linkContainer);
    }

    @Override
    public boolean stillValid(Player player) {
        return be == null || !be.isRemoved();
    }
}
