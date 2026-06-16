package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTier;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * GregTech soft-dependency compat. This class imports GregTech types, so it must only be loaded
 * when GregTech is present — every caller guards on {@link Mods#GTCEU}. Its Forge event handlers are
 * registered manually (not via {@code @Mod.EventBusSubscriber}) for the same reason; see
 * {@link #registerEvents()}.
 */
public class GTCompat {
    /** Registers the deferred-sync Forge event handlers. Call only when GregTech is present. */
    public static void registerEvents() {
        MinecraftForge.EVENT_BUS.register(GTCompat.class);
    }

    public static boolean isGTMachine(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity;
    }

    /**
     * LDLib block entities (GT machines and pipes) sync to clients through managed-field packets,
     * not {@code getUpdatePacket()}, so a paste must explicitly schedule a sync (see
     * {@link #scheduleClientSync}). Covers both so a pipe's pasted paint color renders.
     */
    public static boolean needsClientSync(BlockEntity be) {
        return be instanceof IAutoSyncBlockEntity;
    }

    /**
     * Re-initializes a pasted machine's covers. Loading the cover NBT restores the cover data, but
     * the cover container must be told to load (as on a normal chunk load) or the covers won't
     * render or open until the block is refreshed. Mirrors SpatialToolsCompatible's reinit step.
     */
    public static void reinitCovers(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity mbe) {
            mbe.metaMachine.getCoverContainer().onLoad();
        }
    }

    /**
     * True if this machine writes its contents into the dropped item when broken (so its
     * vanilla drops already carry them). The build loop must not also reclaim its item
     * handler, or the contents would be duplicated.
     */
    public static boolean savesContentsToItem(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity mbe
                && mbe.metaMachine instanceof IDropSaveMachine dropSave
                && dropSave.saveBreak();
    }

    private record PendingSync(ServerLevel level, BlockPos pos, long queuedAt) {}

    private static final List<PendingSync> PENDING_SYNCS = new ArrayList<>();

    /**
     * Schedules a full LDLib field sync for the machine at {@code pos}.
     * <p>
     * LDLib block entities (GT machines) don't implement {@code getUpdatePacket()}: the
     * end-of-tick block-change broadcast carries no BE data, and a payload sent during
     * the placing tick would reach the client before the block-update packet that creates
     * the client-side BE — and be dropped. The sync must go out one tick later, after the
     * client knows the block.
     */
    public static void scheduleClientSync(ServerLevel level, BlockPos pos) {
        PENDING_SYNCS.add(new PendingSync(level, pos.immutable(), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || PENDING_SYNCS.isEmpty()
                || !(event.level instanceof ServerLevel level))
            return;
        Iterator<PendingSync> it = PENDING_SYNCS.iterator();
        while (it.hasNext()) {
            PendingSync sync = it.next();
            if (sync.level != level) continue;
            // The block-update broadcast happens during the next tick's chunk tick;
            // flush only once a full tick has passed so the client BE exists.
            if (level.getGameTime() <= sync.queuedAt) continue;
            it.remove();
            if (level.isLoaded(sync.pos) && level.getBlockEntity(sync.pos) instanceof IAutoSyncBlockEntity syncBE) {
                syncBE.syncNow(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        PENDING_SYNCS.removeIf(sync -> sync.level == event.getLevel());
    }

    /** Returns all cover items that would need to be supplied to fully reproduce this machine's covers. */
    public static List<ItemStack> captureCovers(BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mbe)) return List.of();
        ICoverable covers = mbe.metaMachine.getCoverContainer();
        List<ItemStack> items = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            CoverBehavior cover = covers.getCoverAtSide(dir);
            if (cover == null) continue;
            ItemStack item = cover.getPickItem();
            if (!item.isEmpty()) items.add(item.copy());
            for (ItemStack extra : cover.getAdditionalDrops()) {
                if (!extra.isEmpty()) items.add(extra.copy());
            }
        }
        return items;
    }

    /**
     * Called after {@code be.load()} in COPYING mode. For each cover restored from NBT,
     * consumes the cover item from the player's inventory. If the player can't supply it,
     * the cover is removed without dropping — it was fabricated from the copy template, so
     * handing it back would mint a free cover.
     */
    public static void filterCovers(BlockEntity be, MMInventory inv) {
        if (!(be instanceof MetaMachineBlockEntity mbe)) return;
        ICoverable covers = mbe.metaMachine.getCoverContainer();
        boolean creative = inv.getPlayer().isCreative();
        for (Direction dir : Direction.values()) {
            CoverBehavior cover = covers.getCoverAtSide(dir);
            if (cover == null) continue;
            ItemStack coverItem = cover.getPickItem().copy();
            if (coverItem.isEmpty()) continue;
            if (creative || inv.canConsume(coverItem)) {
                inv.consume(coverItem);
                for (ItemStack extra : cover.getAdditionalDrops()) {
                    if (!extra.isEmpty() && (creative || inv.canConsume(extra))) {
                        inv.consume(extra.copy());
                    }
                }
            } else {
                // Can't pay for this copied cover: remove it without dropping. Don't give it back —
                // it was rebuilt from the template, so handing it over would mint a free cover.
                covers.removeCover(false, dir, null);
            }
        }
    }

    /**
     * Detaches every cover from a machine that is about to be removed and returns the items they drop.
     * Unlike {@link #filterCovers} (COPYING: charges fabricated covers, no give-back), this is the
     * removal counterpart — the covers genuinely existed, so the player keeps them. Covers are removed
     * from the container <em>before</em> the build loop computes {@code Block.getDrops}, so a cover is
     * never both detached here and dropped by the machine. No-op (empty list) for non-GT machines.
     */
    public static List<ItemStack> detachCovers(BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mbe)) return List.of();
        ICoverable covers = mbe.metaMachine.getCoverContainer();
        List<ItemStack> items = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            CoverBehavior cover = covers.getCoverAtSide(dir);
            if (cover == null) continue;
            ItemStack item = cover.getPickItem();
            if (!item.isEmpty()) items.add(item.copy());
            for (ItemStack extra : cover.getAdditionalDrops()) {
                if (!extra.isEmpty()) items.add(extra.copy());
            }
            covers.removeCover(false, dir, null); // detach without dropping; we hand the items back ourselves
        }
        return items;
    }

    /**
     * Capability provider exposing the manipulator as a GregTech electric item (EU), so it charges
     * in GT chargers up to {@code Long.MAX_VALUE}. Created only when GregTech is present; without it
     * the item exposes Forge Energy (FE) instead.
     */
    public static ICapabilityProvider createElectricItemProvider(ItemStack stack, MMTier tier) {
        return new ICapabilityProvider() {
            private final LazyOptional<IElectricItem> cap = LazyOptional.of(() -> new MMElectricItem(stack, tier));

            @Override
            public @Nonnull <T> LazyOptional<T> getCapability(
                    @Nonnull Capability<T> capability, @Nullable Direction side) {
                return GTCapability.CAPABILITY_ELECTRIC_ITEM.orEmpty(capability, cap);
            }
        };
    }

    /** {@link IElectricItem} backed by the manipulator's own NBT charge (shared with the build loop). */
    private static class MMElectricItem implements IElectricItem {
        private final ItemStack stack;
        private final MMTier tier;

        MMElectricItem(ItemStack stack, MMTier tier) {
            this.stack = stack;
            this.tier = tier;
        }

        private MMState state() {
            return MMState.of(stack);
        }

        @Override
        public boolean canProvideChargeExternally() {
            return false; // a tool buffer, not a power source for other machines
        }

        @Override
        public boolean chargeable() {
            return true;
        }

        @Override
        public long getMaxCharge() {
            return tier.effectiveMaxEnergy();
        }

        @Override
        public long getCharge() {
            return Math.min(state().getCharge(), getMaxCharge());
        }

        @Override
        public int getTier() {
            return tier.euTier();
        }

        @Override
        public long getTransferLimit() {
            // Don't self-throttle: the manipulator's capacity dwarfs a tier's EU/t, so a
            // per-tier limit would make charging take ages. The charger's own output is the cap.
            return getMaxCharge();
        }

        @Override
        public long charge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean simulate) {
            if (stack.getCount() != 1 || amount <= 0 /* || chargerTier < getTier() */) return 0;
            if (!ignoreTransferLimit) amount = Math.min(amount, getTransferLimit());
            long charged = Math.min(amount, getMaxCharge() - getCharge());
            if (!simulate && charged > 0) state().setCharge(getCharge() + charged);
            return Math.max(0, charged);
        }

        @Override
        public long discharge(
                long amount, int chargerTier, boolean ignoreTransferLimit, boolean externally, boolean simulate) {
            // Block external dischargers from draining the tool; it only spends energy on its builds.
            if (externally || stack.getCount() != 1 || amount <= 0 || chargerTier < getTier()) return 0;
            if (!ignoreTransferLimit) amount = Math.min(amount, getTransferLimit());
            long discharged = Math.min(amount, getCharge());
            if (!simulate && discharged > 0) state().setCharge(getCharge() - discharged);
            return discharged;
        }
    }

    /**
     * Opens this pipe's connections toward adjacent pipes of the same type and color only — not to
     * machines. GT pipes don't auto-connect on placement, so the cable-mode build calls this after
     * placing each one. {@code setConnection} mirrors the link onto the neighbor, so a later-placed
     * pipe links up the ones already down. No-op for non-pipes.
     */
    public static void connectPipe(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity<?, ?> pipe)) return;
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(pos.relative(side)) instanceof PipeBlockEntity<?, ?> other
                    && other.getPipeType().getClass() == pipe.getPipeType().getClass()
                    && other.getPaintingColor() == pipe.getPaintingColor()) {
                pipe.setConnection(side, true, false);
            }
        }
    }

    /**
     * Parsed GregTech tile state for a copied machine or pipe: the player-facing config NBT (a
     * whitelist of {@link #ANALYSIS_KEYS}, including the {@code "cover"} blob) plus the cover items
     * for the required-items tally. Reproduces the machine through the same recreate-then-reinit path
     * the build loop used before, so GT init order (ctor → load → onLoad) stays correct. Facing lives
     * in the block state and is handled by {@code BuildGenerator}, not here.
     */
    public static class GTAnalysis implements ITileAnalysis {
        /** Player-facing config kept from a GT machine's NBT; drops runtime/positional state. */
        private static final String[] ANALYSIS_KEYS = {
                "ownerUUID", "paintingColor", "cover", "isFlipped",
                "workingMode", "voidingMode", "batchEnabled", "isWorkingEnabled", "workingEnabled",
                "isMuffled", "isDistinct", "currentParallel", "durationMultiplier", "circuitInventory",
                "activeRecipeType", "outputFacingItems", "outputFacingFluids", "inputFacingItems",
                "inputFacingFluids", "autoOutputItems", "autoOutputFluids",
                "allowInputFromOutputSideItems", "allowInputFromOutputSideFluids",
        };

        private CompoundTag config;
        @Nullable
        private List<ItemStack> coverItems;

        /** Captures a machine/pipe's config, or null if {@code be} is neither. */
        @Nullable
        public static GTAnalysis analyze(BlockEntity be) {
            if (!(be instanceof MetaMachineBlockEntity) && !(be instanceof PipeBlockEntity)) return null;
            CompoundTag full = be.saveWithFullMetadata();
            CompoundTag config = new CompoundTag();
            if (full.contains("id")) config.putString("id", full.getString("id"));
            for (String key : ANALYSIS_KEYS) {
                Tag value = full.get(key);
                if (value != null) config.put(key, value.copy());
            }
            GTAnalysis a = new GTAnalysis();
            a.config = config;
            a.coverItems = captureCovers(be);
            return a;
        }

        /** Rebuilds an analysis from previously captured config NBT (see {@link #toNbt}). */
        public static GTAnalysis fromConfig(CompoundTag config) {
            GTAnalysis a = new GTAnalysis();
            a.config = config.copy();
            return a;
        }

        /** A reduced analysis keeping only a pipe's paint color (cables mode), or null if unpainted. */
        @Nullable
        public GTAnalysis colorOnly() {
            if (!config.contains("paintingColor")) return null;
            CompoundTag c = new CompoundTag();
            if (config.contains("id")) c.putString("id", config.getString("id"));
            c.put("paintingColor", config.get("paintingColor").copy());
            GTAnalysis a = new GTAnalysis();
            a.config = c;
            return a;
        }

        /** The captured config NBT, for persisting a cables-mode analysis on the tool. */
        public CompoundTag toNbt() {
            return config.copy();
        }

        @Override
        public boolean apply(BlockApplyContext ctx) {
            ServerLevel level = ctx.level();
            BlockPos pos = ctx.pos();
            BlockState state = level.getBlockState(pos);
            CompoundTag tag = config.copy();
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            // GT machines must initialize in chunk-load order (ctor → load → onLoad); loading NBT into
            // a live machine leaves its subscriptions/multiblock state initialized against pre-load data.
            BlockEntity recreated = BlockEntity.loadStatic(pos, state, tag);
            if (recreated != null) {
                level.setBlockEntity(recreated);
            } else {
                BlockEntity existing = level.getBlockEntity(pos);
                if (existing != null) existing.load(tag);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                filterCovers(be, ctx.inv()); // re-installs + charges covers (no give-back)
                reinitCovers(be);
                be.setChanged();
                if (needsClientSync(be)) scheduleClientSync(level, pos);
            }
            return true;
        }

        @Override
        public void collectRequiredItems(RequiredItems out) {
            if (coverItems != null) {
                for (ItemStack s : coverItems) out.add(s);
            }
        }

        @Override
        public void transform(MMTransform transform) {
            transformMachineNbt(config, transform);
        }

        @Override
        public ITileAnalysis copy() {
            GTAnalysis dup = new GTAnalysis();
            dup.config = config.copy();
            if (coverItems != null) {
                dup.coverItems = new ArrayList<>(coverItems.size());
                for (ItemStack s : coverItems) dup.coverItems.add(s.copy());
            }
            return dup;
        }

        @Override
        public void contributePreviewNbt(CompoundTag out) {
            for (String key : config.getAllKeys()) {
                out.put(key, config.get(key).copy());
            }
        }
    }

    /** Single-direction auto-I/O sides stored directly in a GT machine's NBT, rotated on transform. */
    private static final String[] GT_FACING_KEYS = {
            "outputFacingItems", "outputFacingFluids", "inputFacingItems", "inputFacingFluids",
    };

    /**
     * Rotates/flips the orientation baked into a GT machine's NBT in place: permutes the per-side
     * cover sub-tags (and the {@code attachedSide} int inside each), rotates the auto-I/O facing keys,
     * and toggles a multiblock controller's {@code isFlipped} on a mirroring transform. No-op for NBT
     * that isn't a GregTech machine. Shared by {@link GTAnalysis} (copy) and {@code RawTileAnalysis}
     * (move), so a rotated paste behaves the same either way.
     */
    public static void transformMachineNbt(CompoundTag tag, MMTransform transform) {
        if (!tag.getString("id").startsWith("gtceu:")) return;
        if (tag.contains("cover", Tag.TAG_COMPOUND)) {
            CompoundTag covers = tag.getCompound("cover");
            CompoundTag rotated = covers.copy();
            for (Direction side : Direction.values()) {
                rotated.remove(side.getSerializedName());
            }
            for (Direction side : Direction.values()) {
                Tag blob = covers.get(side.getSerializedName());
                if (blob == null) continue;
                Direction newSide = transform.apply(side);
                Tag moved = blob.copy();
                if (moved instanceof CompoundTag c) {
                    rewriteCoverSide(c, side.ordinal(), newSide.ordinal());
                }
                rotated.put(newSide.getSerializedName(), moved);
            }
            tag.put("cover", rotated);
        }
        for (String key : GT_FACING_KEYS) {
            rotateDirKey(tag, key, transform);
        }
        if (tag.contains("isFlipped") && transform.isReflection()) {
            tag.putBoolean("isFlipped", !tag.getBoolean("isFlipped"));
        }
    }

    private static void rewriteCoverSide(CompoundTag tag, int oldOrdinal, int newOrdinal) {
        for (String key : new ArrayList<>(tag.getAllKeys())) {
            Tag child = tag.get(key);
            if ("side".equals(key) && child instanceof IntTag it && it.getAsInt() == oldOrdinal) {
                tag.putInt(key, newOrdinal);
            } else if (child instanceof CompoundTag c) {
                rewriteCoverSide(c, oldOrdinal, newOrdinal);
            } else if (child instanceof ListTag list) {
                for (Tag el : list) {
                    if (el instanceof CompoundTag ec) rewriteCoverSide(ec, oldOrdinal, newOrdinal);
                }
            }
        }
    }

    private static void rotateDirKey(CompoundTag tag, String key, MMTransform transform) {
        if (tag.contains(key, Tag.TAG_STRING)) {
            Direction d = Direction.byName(tag.getString(key));
            if (d != null) tag.putString(key, transform.apply(d).getSerializedName());
        } else if (tag.contains(key, Tag.TAG_INT)) {
            tag.putInt(key, transform.apply(Direction.from3DDataValue(tag.getInt(key))).get3DDataValue());
        } else if (tag.contains(key, Tag.TAG_BYTE)) {
            tag.putByte(key, (byte) transform.apply(Direction.from3DDataValue(tag.getByte(key))).get3DDataValue());
        }
    }

    /**
     * GregTech "extended facing" correction for the block state, used by {@code BuildGenerator} after
     * the generic per-property facing rewrite. A machine's orientation is (front, upwards, isFlipped);
     * only the front facing is a plain world direction the generic pass gets right. Upwards facing is
     * a horizontal roll whose meaning depends on the front facing, so it must be rebuilt: take the
     * machine's real-world up vector, rotate it, then solve for the upwards-facing value under the new
     * front. {@code original} is the captured pre-transform state; {@code rotated} is the state so far.
     */
    public static BlockState transformBlockState(BlockState rotated, BlockState original, MMTransform transform) {
        if (!(original.getBlock() instanceof MetaMachineBlock block)) return rotated;
        RotationState rs = block.getRotationState();
        if (rs == RotationState.NONE) return rotated;

        Direction oldFront = original.getValue(rs.property);
        Direction newFront = transform.apply(oldFront);
        if (rs.test(newFront) && rotated.hasProperty(rs.property)) {
            rotated = rotated.setValue(rs.property, newFront);
        } else {
            newFront = rotated.getValue(rs.property);
        }

        if (block.getDefinition().isAllowExtendedFacing()
                && original.hasProperty(GTBlockStateProperties.UPWARDS_FACING)) {
            Direction oldUp = original.getValue(GTBlockStateProperties.UPWARDS_FACING);
            Direction worldUp = transform.apply(RelativeDirection.UP.getRelative(oldFront, oldUp, false));
            Direction newUp = solveUpwardsFacing(newFront, worldUp);
            if (newUp != null) {
                rotated = rotated.setValue(GTBlockStateProperties.UPWARDS_FACING, newUp);
            }
        }
        return rotated;
    }

    private static Direction solveUpwardsFacing(Direction front, Direction worldUp) {
        for (Direction candidate : Direction.values()) {
            if (candidate.getAxis() == Direction.Axis.Y) continue; // upwards facing is horizontal
            if (RelativeDirection.UP.getRelative(front, candidate, false) == worldUp) return candidate;
        }
        return null;
    }
}
