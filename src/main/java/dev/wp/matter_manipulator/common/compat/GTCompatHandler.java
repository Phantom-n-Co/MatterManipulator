package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.compat.GTCompat.GTMachineAnalysis;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.IntPredicate;

/**
 * GregTech {@link CompatHandler}. A thin adapter over {@link GTCompat}'s static methods; imports GT
 * types, so it is only ever instantiated behind a {@code Mods.GTCEU} gate (in {@code MMMod.commonSetup}).
 */
public class GTCompatHandler implements CompatHandler {
    @Override
    @Nullable
    public ITileAnalysis analyze(BlockEntity be) {
        return GTMachineAnalysis.analyze(be);
    }

    @Override
    @Nullable
    public CompoundTag analyzeCable(BlockEntity be) {
        GTMachineAnalysis a = GTMachineAnalysis.analyze(be);
        GTMachineAnalysis color = a == null ? null : a.colorOnly();
        return color == null ? null : color.toNbt();
    }

    @Override
    @Nullable
    public ITileAnalysis reconstructCable(String blockId, CompoundTag nbt) {
        return blockId.startsWith("gtceu:") ? GTMachineAnalysis.fromConfig(nbt) : null;
    }

    @Override
    public void transformRawNbt(CompoundTag data, MMTransform transform) {
        GTCompat.transformMachineNbt(data, transform);
    }

    @Override
    public boolean restoreRaw(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !GTCompat.isGTMachine(be)) return false;
        // GT machines must initialize in chunk-load order (ctor → load → onLoad).
        BlockEntity recreated = BlockEntity.loadStatic(pos, state, tag);
        if (recreated != null) level.setBlockEntity(recreated);
        else be.load(tag);

        return true;
    }

    @Override
    public void afterRawLoad(ServerLevel level, BlockPos pos, BlockEntity be) {
        GTCompat.reinitCovers(be); // no-op for non-GT
        if (GTCompat.needsClientSync(be)) GTCompat.scheduleClientSync(level, pos);
    }

    @Override
    public void salvageBeforeDrops(BlockEntity be, MMInventory inv) {
        if (GTCompat.isGTMachine(be)) {
            for (ItemStack s : GTCompat.detachCovers(be)) inv.give(s); // before getDrops → no double count
        }
    }

    @Override
    public boolean usesNativeDrops(BlockEntity be) {
        return GTCompat.isGTMachine(be);
    }

    @Override
    public boolean skipContentReclaim(BlockEntity be) {
        // Machines that bake contents into their drop already keep them; a stocking part's handler is
        // the network, not local storage — never reclaim either.
        return GTCompat.savesContentsToItem(be) || GTCompat.isStockingPart(be);
    }

    @Override
    public boolean skipTankDrain(BlockEntity be) {
        // An ME stocking hatch's tank is the network, not local storage — never drain it.
        return GTCompat.isStockingPart(be);
    }

    @Override
    @Nullable
    public IntPredicate excludedInventorySlots(BlockEntity be) {
        return GTCompat.excludedInventorySlots(be);
    }
}
