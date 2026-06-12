package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;
import java.util.Objects;

public class BlockSpec {

    public final BlockState state;
    @Nullable
    public final CompoundTag tileData;
    /**
     * Items stored in the source block entity, captured through its item handler
     * capability as {Slot, id, Count, tag} entries. Non-null (possibly empty) when
     * the source exposed a handler. Derived from the world at analysis time for
     * material planning; not persisted and not part of equality.
     */
    @Nullable
    public final ListTag contents;

    public BlockSpec(BlockState state, @Nullable CompoundTag tileData, @Nullable ListTag contents) {
        this.state = state;
        this.tileData = tileData;
        this.contents = contents;
    }

    public BlockSpec(BlockState state, @Nullable CompoundTag tileData) {
        this(state, tileData, null);
    }

    public BlockSpec(BlockState state) {
        this(state, null, null);
    }

    public static BlockSpec air() {
        return new BlockSpec(Blocks.AIR.defaultBlockState());
    }

    public static BlockSpec fromWorld(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        CompoundTag tileData = null;
        ListTag contents = null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            tileData = be.saveWithFullMetadata();
            contents = captureContents(be);
        }
        return new BlockSpec(state, tileData, contents);
    }

    @Nullable
    private static ListTag captureContents(BlockEntity be) {
        var handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return null;
        var list = new ListTag();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var tag = stack.save(new CompoundTag());
            tag.putInt("Slot", slot);
            list.add(tag);
        }
        return list;
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

    public boolean hasTileData() {
        return tileData != null;
    }

    /** Returns the item needed to place this block, or empty if none. */
    public ItemStack toStack() {
        var item = state.getBlock().asItem();
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, 1);
    }

    public CompoundTag toNBT() {
        var tag = new CompoundTag();
        tag.put("state", NbtUtils.writeBlockState(state));
        if (tileData != null) {
            tag.put("tile", tileData.copy());
        }
        return tag;
    }

    public static BlockSpec fromNBT(CompoundTag tag) {
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("state"));
        CompoundTag tileData = tag.contains("tile") ? tag.getCompound("tile") : null;
        return new BlockSpec(state, tileData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSpec other)) return false;
        return state.equals(other.state) && Objects.equals(tileData, other.tileData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, tileData);
    }

    @Override
    public String toString() {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " " + state.getValues();
    }
}
