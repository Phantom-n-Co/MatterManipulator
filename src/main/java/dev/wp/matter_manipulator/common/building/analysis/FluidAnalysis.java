package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Fluids a copied tank held, captured through its Forge fluid-handler capability. Fluids can't be
 * granted for free, so {@link #apply} empties whatever the freshly placed tank came with and — only
 * if the manipulator is bound to an AE2 uplink — refills the captured amounts, paying out of the
 * network. Unbound, the tank is left empty.
 */
public class FluidAnalysis implements ITileAnalysis {
    private ListTag fluids;

    /** Captures a block entity's tank contents, or null if it exposes no fluid handler / is empty. */
    public static FluidAnalysis analyze(BlockEntity be) {
        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (handler == null) return null;
        ListTag list = new ListTag();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            CompoundTag tag = fluid.writeToNBT(new CompoundTag());
            tag.putInt("Tank", tank);
            list.add(tag);
        }
        if (list.isEmpty()) return null;
        FluidAnalysis a = new FluidAnalysis();
        a.fluids = list;
        return a;
    }

    @Override
    public boolean apply(BlockApplyContext ctx) {
        BlockEntity be = ctx.getBlockEntity();
        if (be == null) return true;
        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (handler == null) return true;
        // drain() removes one fluid type per call; bounded in case a handler refills itself
        for (int i = 0; i < 64; i++) {
            FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) break;
        }
        Location uplink = ctx.uplink();
        if (!(Mods.AE2 && uplink != null)) return true;
        for (int i = 0; i < fluids.size(); i++) {
            FluidStack want = FluidStack.loadFluidStackFromNBT(fluids.getCompound(i));
            if (want.isEmpty()) continue;
            int capacity = handler.fill(want, IFluidHandler.FluidAction.SIMULATE);
            if (capacity <= 0) continue;
            int got = AECompat.uplinkExtractFluid(ctx.level(), uplink, want, Math.min(capacity, want.getAmount()));
            if (got <= 0) continue;
            FluidStack give = want.copy();
            give.setAmount(got);
            handler.fill(give, IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    @Override
    public void collectRequiredItems(RequiredItems out) {
        for (int i = 0; i < fluids.size(); i++) {
            out.add(FluidStack.loadFluidStackFromNBT(fluids.getCompound(i)));
        }
    }

    @Override
    public void transform(MMTransform transform) {}

    @Override
    public ITileAnalysis copy() {
        FluidAnalysis dup = new FluidAnalysis();
        dup.fluids = fluids.copy();
        return dup;
    }
}
