package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.compat.ae.UplinkBlockEntity;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

public class AECompat {

    // Ghost/config items (ISegmentedInventory.CONFIG) are intentionally excluded — they are fake items.
    private static final ResourceLocation[] ITEM_KEYS = {
            ISegmentedInventory.UPGRADES, ISegmentedInventory.CELLS, ISegmentedInventory.STORAGE
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

    // ── Uplink network bridge ───────────────────────────────────────────────────────
    // The manipulator stores only the bound block's Location; these resolve the live uplink and
    // expose its network to the build/plan code, which never imports AE2 directly.

    @Nullable
    private static UplinkBlockEntity resolveUplink(ServerLevel level, @Nullable Location loc) {
        if (loc == null) return null;
        ServerLevel target = loc.dimension.equals(level.dimension())
                ? level
                : level.getServer().getLevel(loc.dimension);
        if (target == null || !target.isLoaded(loc.pos)) return null;
        return target.getBlockEntity(loc.pos) instanceof UplinkBlockEntity u ? u : null;
    }

    /** Whether a usable uplink is bound and resolvable at {@code loc}. */
    public static boolean hasUplink(ServerLevel level, @Nullable Location loc) {
        return resolveUplink(level, loc) != null;
    }

    /** Whether the block at {@code pos} is an uplink (used by the bind action). */
    public static boolean isUplinkAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof UplinkBlockEntity;
    }

    /** Count of an item available from the uplink (internal buffer + network storage). */
    public static long uplinkAvailable(ServerLevel level, @Nullable Location loc, ItemStack filter) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || filter.isEmpty()) return 0;
        AEItemKey key = AEItemKey.of(filter);
        return key == null ? 0 : u.getUplink().available(key);
    }

    /** Extracts up to {@code amount} of an item from the uplink; returns the amount obtained. */
    public static long uplinkExtract(ServerLevel level, @Nullable Location loc, ItemStack filter, long amount) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || filter.isEmpty() || amount <= 0) return 0;
        AEItemKey key = AEItemKey.of(filter);
        return key == null ? 0 : u.getUplink().extract(key, amount);
    }

    /** Inserts a stack into the uplink's network; returns the count that did not fit. */
    public static int uplinkInsert(ServerLevel level, @Nullable Location loc, ItemStack stack) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || stack.isEmpty()) return stack.getCount();
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) return stack.getCount();
        return (int) u.getUplink().insert(key, stack.getCount());
    }

    /** Fluid (mB) of one type available from the uplink network. */
    public static long uplinkFluidAvailable(ServerLevel level, @Nullable Location loc, FluidStack filter) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || filter.isEmpty()) return 0;
        AEFluidKey key = AEFluidKey.of(filter);
        return key == null ? 0 : u.getUplink().available(key);
    }

    /** Extracts up to {@code amount} mB of a fluid from the uplink; returns mB obtained. */
    public static int uplinkExtractFluid(ServerLevel level, @Nullable Location loc, FluidStack want, int amount) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || want.isEmpty() || amount <= 0) return 0;
        AEFluidKey key = AEFluidKey.of(want);
        return key == null ? 0 : (int) u.getUplink().extract(key, amount);
    }

    /** Draws power from the uplink network; {@code ae} is in AE units, returns AE obtained. */
    public static double uplinkDrawPowerAE(ServerLevel level, @Nullable Location loc, double ae) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        return u == null ? 0 : u.getUplink().drawPowerAE(ae);
    }

    /**
     * Stores a plan pattern (inputs = the given blocks, output = a Plan token) on the uplink for the
     * given player. Returns true if it was stored (false = no uplink or the plan store is full).
     */
    public static boolean uplinkAddPlan(
            ServerLevel level, @Nullable Location loc, UUID player, List<ItemStack> blocks, boolean auto) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null) return false;
        List<GenericStack> stacks = new ArrayList<>(blocks.size());
        for (ItemStack s : blocks) {
            if (s.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(s);
            if (key != null) stacks.add(new GenericStack(key, s.getCount()));
        }
        if (stacks.isEmpty()) return false;
        return u.getUplink().addPlan(player, stacks.toArray(GenericStack[]::new), auto);
    }

    /** Clears the given player's plan patterns from the uplink; returns how many were removed (-1 = no uplink). */
    public static int uplinkClearPlan(ServerLevel level, @Nullable Location loc, UUID player) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null) return -1;
        return u.getUplink().clearPlan(player);
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

        /**
         * Rotates a cable bus's parts and facades to follow a COPYING/MOVING rotation/flip.
         *
         * <p>A cable bus stores each side's part under that side's serialized name
         * ({@code down/up/north/...}; the center cable is keyed {@code "cable"} and has no side) and
         * each facade under {@code "facade" + Capitalized(side)} — both written at the root of the tile
         * NBT by {@code CableBusContainer.writeToNBT}/{@code FacadeContainer.writeToNBT}. A part takes
         * its side purely from which key it sits under ({@code AEBasePart.side} is not serialized; it's
         * set from the slot on load), so moving each side blob to the transform-rotated key is all that's
         * needed — unlike GT covers, there is no side baked inside the blob to rewrite.
         */
        @Override
        public CompoundTag transformTileData(BlockState state, CompoundTag tileData, MMTransform transform) {
            if (!tileData.getString("id").equals("ae2:cable_bus")) return tileData;
            permuteSideKeys(tileData, transform, side -> side.getSerializedName());
            permuteSideKeys(tileData, transform, side -> "facade" + StringUtils.capitalize(side.getSerializedName()));
            return tileData;
        }

        /** Moves each per-side sub-tag (keyed by {@code keyFor}) to the transform-rotated side. */
        private static void permuteSideKeys(
                CompoundTag tag, MMTransform transform, java.util.function.Function<Direction, String> keyFor) {
            java.util.Map<String, Tag> moved = new java.util.HashMap<>();
            for (Direction side : Direction.values()) {
                Tag blob = tag.get(keyFor.apply(side));
                if (blob != null) moved.put(keyFor.apply(transform.apply(side)), blob.copy());
            }
            for (Direction side : Direction.values()) {
                tag.remove(keyFor.apply(side));
            }
            moved.forEach(tag::put);
        }
    }
}
