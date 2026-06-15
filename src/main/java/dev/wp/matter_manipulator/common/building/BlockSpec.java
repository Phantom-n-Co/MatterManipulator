package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

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

    /**
     * Additional items that must be supplied when pasting in COPYING mode — GT covers,
     * AE2 parts, upgrades, and cells captured via mod-specific APIs. Derived from the
     * world; not persisted and not part of equality.
     */
    @Nullable
    public List<ItemStack> extraItems;

    /**
     * Fluids stored in the source block entity, captured through its Forge fluid-handler
     * capability as {Tank, ...FluidStack} entries. Derived from the world; not persisted and
     * not part of equality. Used by COPYING to refill tanks from a bound AE2 network.
     */
    @Nullable
    public final ListTag fluids;

    public BlockSpec(
            BlockState state,
            @Nullable CompoundTag tileData,
            @Nullable ListTag contents,
            @Nullable List<ItemStack> extraItems,
            @Nullable ListTag fluids) {
        this.state = state;
        this.tileData = tileData;
        this.contents = contents;
        this.extraItems = extraItems;
        this.fluids = fluids;
    }

    public BlockSpec(
            BlockState state,
            @Nullable CompoundTag tileData,
            @Nullable ListTag contents,
            @Nullable List<ItemStack> extraItems) {
        this(state, tileData, contents, extraItems, null);
    }

    public BlockSpec(BlockState state, @Nullable CompoundTag tileData, @Nullable ListTag contents) {
        this(state, tileData, contents, null, null);
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
        ListTag fluids = null;
        List<ItemStack> extraItems = null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            tileData = be.saveWithFullMetadata();
            contents = captureContents(be);
            fluids = captureFluids(be);
            List<ItemStack> extras = new ArrayList<>();
            if (Mods.GTCEU && GTCompat.isGTMachine(be)) extras.addAll(GTCompat.captureCovers(be));
            if (Mods.AE2 && AECompat.isAEBlock(be)) extras.addAll(AECompat.captureAEItems(be));
            if (!extras.isEmpty()) extraItems = extras;
        }
        return new BlockSpec(state, tileData, contents, extraItems, fluids);
    }

    @Nullable
    private static ListTag captureFluids(BlockEntity be) {
        var handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (handler == null) return null;
        var list = new ListTag();
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            var tag = fluid.writeToNBT(new CompoundTag());
            tag.putInt("Tank", tank);
            list.add(tag);
        }
        return list.isEmpty() ? null : list;
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
        if (isItemlessBlock()) return ItemStack.EMPTY;
        return new ItemStack(item, 1);
    }

    /**
     * Blocks whose registered item can't be obtained or placed directly, so their real cost is
     * carried by other items. The AE2 cable bus is a virtual host with a placeholder block item
     * ("AE2 Cable and/or Bus"); its cable and parts are charged separately as extra items.
     */
    private boolean isItemlessBlock() {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && key.toString().equals("ae2:cable_bus");
    }

    /**
     * A user-facing name for this block. For an itemless host like an AE2 cable bus the block's own
     * name is a useless placeholder, so the captured contents (cable/part) name it instead.
     */
    public Component getDisplayName() {
        if (isItemlessBlock() && extraItems != null && !extraItems.isEmpty()) {
            return extraItems.get(0).getHoverName();
        }
        return state.getBlock().getName();
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
