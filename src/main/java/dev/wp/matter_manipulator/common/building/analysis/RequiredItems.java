package dev.wp.matter_manipulator.common.building.analysis;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A running tally of the items and fluids a build would require, accumulated by each
 * {@link ITileAnalysis#collectRequiredItems} (plus the block items themselves). Insertion-ordered so
 * the player-facing list reads in a stable order. Items keep their full NBT (GT rotor material, circuit
 * config, etc.), merging only stacks that are {@link ItemStack#isSameItemSameTags same item and tags}.
 */
public class RequiredItems {
    /** One distinct (item + NBT) variant and how many the build needs. */
    public static final class ItemEntry {
        /** A count-1 representative of the variant; read its name/NBT and rebuild request stacks from it. */
        public final ItemStack proto;
        public long count;

        ItemEntry(ItemStack proto, long count) {
            this.proto = proto;
            this.count = count;
        }
    }

    public final List<ItemEntry> items = new ArrayList<>();
    public final Map<Fluid, Long> fluids = new LinkedHashMap<>();

    public void add(ItemStack stack) {
        if (stack.isEmpty()) return;
        // ponytail: linear merge, dozens of entries; key on an NBT wrapper if it grows
        for (ItemEntry e : items) {
            if (ItemStack.isSameItemSameTags(e.proto, stack)) {
                e.count += stack.getCount();
                return;
            }
        }
        items.add(new ItemEntry(stack.copyWithCount(1), stack.getCount()));
    }

    public void add(FluidStack stack) {
        if (!stack.isEmpty()) addFluid(stack.getFluid(), stack.getAmount());
    }

    public void addFluid(Fluid fluid, long amount) {
        if (amount > 0) fluids.merge(fluid, amount, Long::sum);
    }

    public boolean isEmpty() {
        return items.isEmpty() && fluids.isEmpty();
    }
}
