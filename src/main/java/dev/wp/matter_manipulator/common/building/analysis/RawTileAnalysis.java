package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.compat.CompatRegistry;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Verbatim tile NBT for a MOVING build, which relocates a block exactly (contents, energy, progress)
 * and charges nothing — the block is the same one, just somewhere else. Unlike the copy analyses,
 * this keeps the full block-entity state. {@link #transform} still rotates the orientation baked into
 * the NBT (GT covers/facing, AE part sides) so a moved-and-rotated paste lands correctly.
 */
public class RawTileAnalysis implements ITileAnalysis {
    private CompoundTag data;

    public static RawTileAnalysis of(CompoundTag fullNbt) {
        RawTileAnalysis a = new RawTileAnalysis();
        a.data = fullNbt.copy();
        return a;
    }

    @Override
    public boolean apply(BlockApplyContext ctx) {
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.pos();
        BlockState state = level.getBlockState(pos);
        CompoundTag tag = data.copy();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        BlockEntity be = level.getBlockEntity(pos);
        if (!CompatRegistry.restoreRaw(level, pos, state, tag)) {
            if (be != null) be.load(tag);
        }

        be = level.getBlockEntity(pos);
        if (be != null) {
            CompatRegistry.afterRawLoad(level, pos, be);
            be.setChanged();
        }
        return true;
    }

    @Override
    public void collectRequiredItems(RequiredItems out) {
        // MOVING relocates the existing block; it needs no items.
    }

    @Override
    public void transform(MMTransform transform) {
        CompatRegistry.transformRawNbt(data, transform);
    }

    @Override
    public ITileAnalysis copy() {
        return of(data);
    }

    @Override
    public void contributePreviewNbt(CompoundTag out) {
        for (String key : data.getAllKeys()) {
            out.put(key, data.get(key).copy());
        }
    }
}
