package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
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
import net.minecraftforge.fluids.FluidStack;

import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.definitions.ColoredItemDefinition;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.items.parts.ColoredPartItem;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

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
     * NBT, consumes the item from the player's inventory. Anything the player can't pay for is
     * removed (parts) or cleared (upgrades/cells) — never handed back: these were fabricated from
     * the copy template, not taken from the player, so giving them out would mint free items.
     */
    public static void filterAEItems(BlockEntity be, MMInventory inv) {
        boolean creative = inv.getPlayer().isCreative();
        if (be instanceof IPartHost partHost) {
            for (Direction dir : PART_SIDES) {
                IPart part = partHost.getPart(dir);
                if (part == null) continue;
                ItemStack partItem = new ItemStack(part.getPartItem());
                if (creative || canConsumeCable(inv, partItem)) {
                    consumeCable(inv, partItem);
                    if (part instanceof ISegmentedInventory seg) {
                        filterSegmented(seg, inv, creative);
                    }
                } else {
                    // Can't pay for this copied part: remove it from the world. removePartFromSide
                    // detaches it without dropping, and we must not give it back — it was rebuilt
                    // from the template, so handing it over would mint a free part.
                    partHost.removePartFromSide(dir);
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
                    // Fabricated from the template — remove it, don't hand the player a free facade.
                    fc.removeFacade(partHost, dir);
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
                    // Fabricated from the template — clear it, don't grant a free cell/upgrade.
                    internal.setItemDirect(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /** The colour family of a cable part item, or null if it isn't a colored cable. */
    @Nullable
    private static ColoredItemDefinition<?> cableFamily(ItemStack stack) {
        if (!(stack.getItem() instanceof ColoredPartItem<?> cable)) return null;
        for (ColoredItemDefinition<?> def : AEParts.COLORED_PARTS) {
            if (def.item(cable.getColor()) == cable) return def; // items are singletons
        }
        return null;
    }

    /**
     * Whether a cable part is affordable in ANY colour (the manipulator paints cables for free), or
     * the exact item if it isn't a cable.
     */
    private static boolean canConsumeCable(MMInventory inv, ItemStack partItem) {
        ColoredItemDefinition<?> fam = cableFamily(partItem);
        if (fam == null) return inv.canConsume(partItem);
        for (AEColor c : AEColor.values()) {
            ItemStack v = fam.stack(c);
            if (!v.isEmpty() && inv.canConsume(v)) return true; // canConsume(EMPTY) is true — guard it
        }
        return false;
    }

    /** Consumes one cable of any available colour (or the exact item for non-cables). */
    private static void consumeCable(MMInventory inv, ItemStack partItem) {
        ColoredItemDefinition<?> fam = cableFamily(partItem);
        if (fam == null) {
            inv.consume(partItem);
            return;
        }
        for (AEColor c : AEColor.values()) {
            ItemStack v = fam.stack(c);
            if (!v.isEmpty() && inv.canConsume(v)) {
                inv.consume(v);
                return;
            }
        }
    }

    /**
     * Removal counterpart of {@link #captureAEItems}: detaches every part and facade and empties the
     * cell/upgrade/storage inventories, returning everything that should be refunded to the player when
     * the build overwrites this AE2 block. Mirrors the original {@code AbstractBuildable.resetAEMachine}
     * — AE blocks are disassembled by hand (not via {@code Block.getDrops}), so the build loop must not
     * also drop them. {@code removePartFromSide}/{@code removeFacade} detach without spilling.
     */
    public static List<ItemStack> dismantle(BlockEntity be) {
        List<ItemStack> drops = new ArrayList<>();
        if (be instanceof IPartHost partHost) {
            for (Direction dir : PART_SIDES) {
                IPart part = partHost.getPart(dir);
                if (part == null) continue;
                ItemStack partItem = new ItemStack(part.getPartItem());
                ColoredItemDefinition<?> fam = cableFamily(partItem);
                // Cables come back un-dyed (fluix); colour is free, so we don't refund a dyed one.
                drops.add(fam == null ? partItem : fam.stack(AEColor.TRANSPARENT));
                if (part instanceof ISegmentedInventory seg) collectSegmented(seg, drops);
                partHost.removePartFromSide(dir);
            }
            IFacadeContainer fc = partHost.getFacadeContainer();
            for (Direction dir : Direction.values()) {
                IFacadePart facade = fc.getFacade(dir);
                if (facade == null) continue;
                drops.add(facade.getItemStack().copy());
                fc.removeFacade(partHost, dir);
            }
        } else if (be instanceof ISegmentedInventory seg) {
            collectSegmented(seg, drops);
            clearSegmented(seg);
        }
        return drops;
    }

    private static void clearSegmented(ISegmentedInventory seg) {
        for (ResourceLocation key : ITEM_KEYS) {
            InternalInventory internal = seg.getSubInventory(key);
            if (internal == null) continue;
            for (int i = 0; i < internal.size(); i++) {
                if (!internal.getStackInSlot(i).isEmpty()) internal.setItemDirect(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Nudges a freshly pasted cable bus to refresh, so attached parts (storage/import buses) re-scan
     * their adjacent inventory and the host re-broadcasts to clients. The modern equivalent of the
     * original's AE2 part-cache reset; combined with the build loop's neighbour notification this lets
     * a pasted bus connect immediately instead of after the next block update. No-op for non-AE blocks.
     */
    public static void markHostForUpdate(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IPartHost host) {
            host.markForUpdate();
        }
    }

    /**
     * If {@code encoded} is an AE2 encoded pattern, pays for up to {@code count} of it with BLANK
     * patterns (player inventory + bound network) and returns how many blanks were consumed. The caller
     * then places the captured encoded stack — whose NBT already holds the recipe — for that many,
     * fabricating it from a blank (mirrors the original {@code PatternItemProvider}). Returns 0 for
     * non-patterns, so no other item is ever substituted.
     */
    public static int payWithBlankPattern(MMInventory inv, ItemStack encoded, int count) {
        if (count <= 0 || !(encoded.getItem() instanceof EncodedPatternItem)) return 0;
        return inv.consumeUpTo(new ItemStack(AEItems.BLANK_PATTERN.asItem()), count);
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

    /** Inserts a fluid into the uplink's network; returns the mB that did not fit (all of it if unbound). */
    public static int uplinkInsertFluid(ServerLevel level, @Nullable Location loc, FluidStack stack) {
        UplinkBlockEntity u = resolveUplink(level, loc);
        if (u == null || stack.isEmpty()) return stack.getAmount();
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) return stack.getAmount();
        return (int) u.getUplink().insert(key, stack.getAmount());
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

            AEItemKey key = cableFamily(s) != null ? AEItemKey.of(cableFamily(s).stack(AEColor.TRANSPARENT)) : AEItemKey.of(s);
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
     * Parsed AE2 tile state for a copied block. AE2 re-initialises its own grid/node state when a
     * block is placed, so the captured BE NBT can be loaded wholesale and it correctly reproduces
     * parts, facades, cells, and configuration; this analysis therefore stores that NBT plus the real
     * items (parts/facades/cells/upgrades) needed for the required-items tally, and charges them in
     * {@link #apply} (unaffordable items are removed, never given back — see {@link #filterAEItems}).
     * {@link #transform} permutes the per-side part/facade keys; {@link #forCable}/{@link #cableOnly}
     * build a bare center-cable analysis for cables mode (mirroring the original {@code getAECables}).
     */
    public static class AEAnalysis implements ITileAnalysis {
        private CompoundTag data;
        @Nullable
        private List<ItemStack> items;

        @Nullable
        public static AEAnalysis analyze(BlockEntity be) {
            if (!isAEBlock(be)) return null;
            AEAnalysis a = new AEAnalysis();
            a.data = be.saveWithFullMetadata();
            a.items = captureAEItems(be);
            return a;
        }

        /** A bare center-cable analysis from a cable part item (cables mode). */
        @Nullable
        public static AEAnalysis forCable(ItemStack cableStack) {
            if (!(cableStack.getItem() instanceof IPartItem<?> partItem)) return null;
            CompoundTag cable = new CompoundTag();
            cable.putString("id", IPartItem.getId(partItem).toString());
            CompoundTag data = new CompoundTag();
            data.putString("id", "ae2:cable_bus");
            data.put("cable", cable);
            AEAnalysis a = new AEAnalysis();
            a.data = data;
            a.items = List.of(new ItemStack(partItem));
            return a;
        }

        /** Reduce a captured cable bus to just its center cable, dropping attached parts and facades. */
        @Nullable
        public AEAnalysis cableOnly() {
            if (!data.getString("id").equals("ae2:cable_bus")) return null;
            Tag center = data.get("cable");
            if (!(center instanceof CompoundTag cableBlob)) return null;
            CompoundTag d = new CompoundTag();
            d.putString("id", "ae2:cable_bus");
            d.put("cable", cableBlob.copy());
            AEAnalysis a = new AEAnalysis();
            a.data = d;
            IPartItem<?> partItem = IPartItem.byId(ResourceLocation.parse(cableBlob.getString("id")));
            a.items = partItem == null ? List.of() : List.of(new ItemStack(partItem));
            return a;
        }

        /** Rebuilds a (cable) analysis from previously captured NBT (see {@link #toNbt}). */
        public static AEAnalysis fromData(CompoundTag data) {
            AEAnalysis a = new AEAnalysis();
            a.data = data.copy();
            List<ItemStack> items = new ArrayList<>();
            if (data.get("cable") instanceof CompoundTag cable) {
                IPartItem<?> partItem = IPartItem.byId(ResourceLocation.parse(cable.getString("id")));
                if (partItem != null) items.add(new ItemStack(partItem));
            }
            a.items = items;
            return a;
        }

        /** The captured NBT, for persisting a cables-mode analysis on the tool. */
        public CompoundTag toNbt() {
            return data.copy();
        }

        @Override
        public boolean apply(BlockApplyContext ctx) {
            BlockEntity be = ctx.getBlockEntity();
            if (be == null) return true;
            CompoundTag tag = data.copy();
            BlockPos pos = ctx.pos();
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            be.load(tag);
            filterAEItems(be, ctx.inv());
            be.setChanged();
            return true;
        }

        @Override
        public void collectRequiredItems(RequiredItems out) {
            if (items != null) {
                // Cables are painted for free — always plan them as FLUIX, never a dyed variant.
                for (ItemStack s : items) {
                    ColoredItemDefinition<?> fam = cableFamily(s);
                    out.add(fam == null ? s : fam.stack(AEColor.TRANSPARENT));
                }
            }
        }

        @Override
        public void transform(MMTransform transform) {
            transformCableBusNbt(data, transform);
        }

        @Override
        public ITileAnalysis copy() {
            AEAnalysis dup = new AEAnalysis();
            dup.data = data.copy();
            if (items != null) {
                dup.items = new ArrayList<>(items.size());
                for (ItemStack s : items) dup.items.add(s.copy());
            }
            return dup;
        }

        @Override
        public void contributePreviewNbt(CompoundTag out) {
            for (String key : data.getAllKeys()) {
                out.put(key, data.get(key).copy());
            }
        }
    }

    /**
     * Rotates/flips a cable bus's parts and facades in place by moving each per-side sub-tag to the
     * transform-rotated side. A part takes its side purely from which key it sits under
     * ({@code AEBasePart.side} is not serialized), so moving the blob is all that's needed — no side
     * is baked inside it (unlike GT covers). No-op for NBT that isn't a cable bus. Shared by
     * {@link AEAnalysis} (copy) and {@code RawTileAnalysis} (move).
     */
    public static void transformCableBusNbt(CompoundTag tag, MMTransform transform) {
        if (!tag.getString("id").equals("ae2:cable_bus")) return;
        permuteSideKeys(tag, transform, side -> side.getSerializedName());
        permuteSideKeys(tag, transform, side -> "facade" + StringUtils.capitalize(side.getSerializedName()));
    }

    /** Moves each per-side sub-tag (keyed by {@code keyFor}) to the transform-rotated side. */
    private static void permuteSideKeys(
            CompoundTag tag, MMTransform transform, Function<Direction, String> keyFor) {
        Map<String, Tag> moved = new HashMap<>();
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
