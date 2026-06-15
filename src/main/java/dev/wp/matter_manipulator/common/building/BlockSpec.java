package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The identity of a block to place: just its {@link BlockState}. Captured tile state (machine config,
 * AE parts, covers, container contents, fluids) lives in separate {@code ITileAnalysis} objects
 * attached to a {@link PendingBlock} — see {@link dev.wp.matter_manipulator.common.building.analysis}.
 * This mirrors the original mod, where the spec is lightweight and analysis is opt-in per copy.
 */
public class BlockSpec {
    public final BlockState state;

    public BlockSpec(BlockState state) {
        this.state = state;
    }

    public static BlockSpec air() {
        return new BlockSpec(Blocks.AIR.defaultBlockState());
    }

    /** The (lightweight) spec of the block at {@code pos}; tile state is captured via analyses. */
    public static BlockSpec fromWorld(Level level, BlockPos pos) {
        return new BlockSpec(level.getBlockState(pos));
    }

    public boolean isAir() {
        return state.isAir();
    }

    public boolean isFree() {
        return isAir() || state.getBlock() == Blocks.WATER || state.getBlock() == Blocks.LAVA;
    }

    public boolean skipWhenCopying() {
        return isAir();
    }

    /** Returns the item needed to place this block, or empty if none. */
    public ItemStack toStack() {
        var item = state.getBlock().asItem();
        if (item == Items.AIR) return ItemStack.EMPTY;
        if (isItemlessBlock()) return ItemStack.EMPTY;
        return new ItemStack(item, 1);
    }

    /**
     * Blocks whose registered item can't be obtained or placed directly, so their real cost is
     * carried by other items. The AE2 cable bus is a virtual host with a placeholder block item
     * ("AE2 Cable and/or Bus"); its cable and parts are charged separately as analyses.
     */
    public boolean isItemlessBlock() {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && key.toString().equals("ae2:cable_bus");
    }

    public Component getDisplayName() {
        return state.getBlock().getName();
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.put("state", NbtUtils.writeBlockState(state));
        return tag;
    }

    public static BlockSpec fromNBT(CompoundTag tag) {
        return new BlockSpec(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("state")));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSpec other)) return false;
        return state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    @Override
    public String toString() {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state.getValues();
    }
}
