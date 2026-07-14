package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.compat.AECompat.AEAnalysis;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * AE2 {@link CompatHandler}. A thin adapter over {@link AECompat}'s static methods; imports AE2 types,
 * so it is only ever instantiated behind a {@code Mods.AE2} gate (in {@code MMMod.commonSetup}).
 */
public class AECompatHandler implements CompatHandler {
    @Override
    @Nullable
    public ITileAnalysis analyze(BlockEntity be) {
        return AEAnalysis.analyze(be);
    }

    @Override
    public boolean isExclusive() {
        // AE2 re-initialises its own grid and reproduces parts/contents wholesale.
        return true;
    }

    @Override
    @Nullable
    public CompoundTag analyzeCable(BlockEntity be) {
        AEAnalysis a = AEAnalysis.analyze(be);
        AEAnalysis cable = a == null ? null : a.cableOnly();
        return cable == null ? null : cable.toNbt();
    }

    @Override
    @Nullable
    public ITileAnalysis reconstructCable(String blockId, CompoundTag nbt) {
        return blockId.equals("ae2:cable_bus") ? AEAnalysis.fromData(nbt) : null;
    }

    @Override
    public void transformRawNbt(CompoundTag data, MMTransform transform) {
        AECompat.transformCableBusNbt(data, transform);
    }

    @Override
    public void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be) {
        AECompat.markHostForUpdate(level, pos);
    }

    @Override
    public boolean restoreRaw(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        if (!"ae2:cable_bus".equals(tag.getString("id"))) return false;
        BlockEntity recreated = BlockEntity.loadStatic(pos, state, tag);
        if (recreated != null) level.setBlockEntity(recreated);
        return true;
    }

    @Override
    public boolean fullyDismantle(BlockEntity be, MMInventory inv) {
        if (!AECompat.isAEBlock(be)) return false;
        // AE blocks drop nothing through Block.getDrops; disassemble them by hand.
        for (ItemStack s : AECompat.dismantle(be)) inv.give(s);
        return true;
    }
}
