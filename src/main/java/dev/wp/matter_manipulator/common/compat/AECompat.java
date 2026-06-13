package dev.wp.matter_manipulator.common.compat;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import dev.wp.matter_manipulator.common.building.MMInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AECompat {

    // Ghost/config items (ISegmentedInventory.CONFIG) are intentionally excluded — they are fake items.
    private static final ResourceLocation[] ITEM_KEYS = {
        ISegmentedInventory.UPGRADES,
        ISegmentedInventory.CELLS,
        ISegmentedInventory.STORAGE
    };

    // Cable-bus part slots. null = the center (the cable itself); the rest are attached parts.
    // The cable bus block has no obtainable item of its own, so the center cable carries that cost.
    private static final Direction[] PART_SIDES = {
        null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static boolean isAEBlock(BlockEntity be) {
        return be instanceof IPartHost || be instanceof ISegmentedInventory;
    }

    /**
     * Returns all real items (parts, upgrades, cells, stored stacks) that would need to be
     * supplied to fully reproduce this AE2 block entity's contents.
     * The cable bus center cable is excluded — it's accounted for by {@code BlockSpec.toStack()}.
     */
    public static List<ItemStack> captureAEItems(BlockEntity be) {
        List<ItemStack> items = new ArrayList<>();
        if (be instanceof IPartHost partHost) {
            for (Direction dir : PART_SIDES) {
                IPart part = partHost.getPart(dir);
                if (part == null) continue;
                items.add(new ItemStack(part.getPartItem()));
                if (part instanceof ISegmentedInventory seg) {
                    collectSegmented(seg, items);
                }
            }
            IFacadeContainer fc = partHost.getFacadeContainer();
            for (Direction dir : Direction.values()) {
                IFacadePart facade = fc.getFacade(dir);
                if (facade != null) items.add(facade.getItemStack().copy());
            }
        } else if (be instanceof ISegmentedInventory seg) {
            collectSegmented(seg, items);
        }
        return items;
    }

    private static void collectSegmented(ISegmentedInventory seg, List<ItemStack> out) {
        for (ResourceLocation key : ITEM_KEYS) {
            InternalInventory inv = seg.getSubInventory(key);
            if (inv == null) continue;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty()) out.add(s.copy());
            }
        }
    }

    /**
     * Called after {@code be.load()} in COPYING mode. For each part/upgrade/cell restored from
     * NBT, consumes the item from the player's inventory. Parts that can't be paid for are
     * removed (with their contents given back); individual upgrades/cells that can't be paid for
     * are cleared and given back.
     */
    public static void filterAEItems(BlockEntity be, MMInventory inv) {
        boolean creative = inv.getPlayer().isCreative();
        if (be instanceof IPartHost partHost) {
            for (Direction dir : PART_SIDES) {
                IPart part = partHost.getPart(dir);
                if (part == null) continue;
                ItemStack partItem = new ItemStack(part.getPartItem());
                if (creative || inv.canConsume(partItem)) {
                    inv.consume(partItem);
                    if (part instanceof ISegmentedInventory seg) {
                        filterSegmented(seg, inv, creative);
                    }
                } else {
                    // Collect all drops (part item + contents) before removing
                    List<ItemStack> drops = new ArrayList<>();
                    part.addPartDrop(drops, false);
                    part.addAdditionalDrops(drops, false);
                    partHost.removePartFromSide(dir);
                    drops.forEach(inv::give);
                }
            }
            IFacadeContainer fc = partHost.getFacadeContainer();
            for (Direction dir : Direction.values()) {
                IFacadePart facade = fc.getFacade(dir);
                if (facade == null) continue;
                ItemStack facadeItem = facade.getItemStack().copy();
                if (creative || inv.canConsume(facadeItem)) {
                    inv.consume(facadeItem);
                } else {
                    fc.removeFacade(partHost, dir);
                    inv.give(facadeItem);
                }
            }
        } else if (be instanceof ISegmentedInventory seg) {
            filterSegmented(seg, inv, creative);
        }
    }

    private static void filterSegmented(ISegmentedInventory seg, MMInventory inv, boolean creative) {
        for (ResourceLocation key : ITEM_KEYS) {
            InternalInventory internal = seg.getSubInventory(key);
            if (internal == null) continue;
            for (int i = 0; i < internal.size(); i++) {
                ItemStack stack = internal.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (creative || inv.canConsume(stack)) {
                    inv.consume(stack);
                } else {
                    inv.give(stack.copy());
                    internal.setItemDirect(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /**
     * Copy rule for AE2 blocks. AE2 re-initializes its own grid/node state when a block is placed,
     * so unlike GT machines the full-NBT path is safe and already reproduces parts, facades, and
     * configuration correctly. To avoid regressing that working path, this handler passes the
     * captured tag through unchanged (keeping AE2 inside the allowlist gate rather than blanking
     * it) and charges the player for parts, upgrades, and cells in {@code afterPaste}.
     *
     * <p>Part/cell contents live behind {@code ISegmentedInventory} and part NBT, not the root
     * item-handler capability, so {@code PendingBuild.injectContents} is skipped for AE2 blocks to
     * avoid any double handling. A finer per-part whitelist via AE2's {@code exportSettings} API is
     * a future refinement; AE2 is lower priority than GT here.
     */
    public static class AECopyHandler implements CopyHandler {

        @Override
        public boolean matches(BlockState state, @Nullable BlockEntity be, @Nullable CompoundTag fullTag) {
            return isAEBlock(be);
        }

        @Override
        public void contributeNbt(CompoundTag full, CompoundTag out, MMInventory inv) {
            for (String key : full.getAllKeys()) {
                Tag value = full.get(key);
                if (value != null) out.put(key, value.copy());
            }
        }

        @Override
        public void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be, CompoundTag full, MMInventory inv) {
            filterAEItems(be, inv);
        }
    }
}
