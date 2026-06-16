package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Items a copied container held, captured through its Forge item-handler capability as
 * {@code {Slot, id, Count, tag}} entries. Layout-independent: it works for any block exposing the
 * capability. On {@link #apply} it refills each slot from the player (capped at what they can supply)
 * and charges them; contents have no orientation, so {@link #transform} is a no-op.
 */
public class InventoryAnalysis implements ITileAnalysis {
    private ListTag contents;

    /** Captures a block entity's item-handler contents, or null if it has none. */
    public static InventoryAnalysis analyze(BlockEntity be) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return null;
        ListTag list = new ListTag();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            CompoundTag tag = stack.save(new CompoundTag());
            tag.putInt("Slot", slot);
            list.add(tag);
        }
        if (list.isEmpty()) return null;
        InventoryAnalysis a = new InventoryAnalysis();
        a.contents = list;
        return a;
    }

    @Override
    public boolean apply(BlockApplyContext ctx) {
        BlockEntity be = ctx.getBlockEntity();
        if (be == null) return true;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (!(handler instanceof IItemHandlerModifiable modifiable)) return true;
        boolean creative = ctx.isCreative();
        for (int i = 0; i < contents.size(); i++) {
            CompoundTag slotTag = contents.getCompound(i);
            int slot = slotTag.getInt("Slot");
            ItemStack stack = ItemStack.of(slotTag);
            if (stack.isEmpty() || slot < 0 || slot >= modifiable.getSlots()) continue;
            int want = stack.getCount();
            int paid;
            if (creative) {
                paid = want;
            } else {
                // Draw from the player AND the bound network (countAvailable), then consume the same way.
                paid = (int) Math.min(want, ctx.inv().countAvailable(stack));
                if (paid > 0) ctx.inv().consume(stack.copyWithCount(paid));
                // An AE2 encoded pattern the player can't supply is fabricated from a blank pattern.
                if (paid < want && Mods.AE2) {
                    paid += AECompat.payWithBlankPattern(ctx.inv(), stack, want - paid);
                }
            }
            if (paid <= 0) continue;
            modifiable.setStackInSlot(slot, stack.copyWithCount(paid));
        }
        return true;
    }

    @Override
    public void collectRequiredItems(RequiredItems out) {
        for (int i = 0; i < contents.size(); i++) {
            out.add(ItemStack.of(contents.getCompound(i)));
        }
    }

    @Override
    public void transform(MMTransform transform) {}

    @Override
    public ITileAnalysis copy() {
        InventoryAnalysis dup = new InventoryAnalysis();
        dup.contents = contents.copy();
        return dup;
    }
}
