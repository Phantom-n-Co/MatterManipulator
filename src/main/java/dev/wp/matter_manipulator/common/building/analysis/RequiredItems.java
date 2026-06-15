package dev.wp.matter_manipulator.common.building.analysis;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A running tally of the items and fluids a build would require, accumulated by each
 * {@link ITileAnalysis#collectRequiredItems} (plus the block items themselves). Insertion-ordered so
 * the player-facing list reads in a stable order.
 */
public class RequiredItems {
    public final Map<Item, Long> items = new LinkedHashMap<>();
    public final Map<Fluid, Long> fluids = new LinkedHashMap<>();

    public void add(ItemStack stack) {
        if (!stack.isEmpty()) add(stack.getItem(), stack.getCount());
    }

    public void add(Item item, long count) {
        if (count > 0) items.merge(item, count, Long::sum);
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
