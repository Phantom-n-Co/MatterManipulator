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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternContainer;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.IMEStockingPart;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import top.ialdaiaxiariyay.bettergtae.common.machine.multiblock.part.ExtendMEPatternBufferPartMachine;
import top.ialdaiaxiariyay.bettergtae.common.machine.multiblock.part.ExtendMEPatternBufferProxyPartMachine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * GregTech soft-dependency compat. This class imports GregTech types, so it must only be loaded
 * when GregTech is present — every caller guards on {@link Mods#GTCEU}. Its Forge event handlers are
 * registered manually (not via {@code @Mod.EventBusSubscriber}) for the same reason; see
 * {@link #registerEvents()}.
 */
public class GTCompat {
    /**
     * Registers the deferred-sync Forge event handlers. Call only when GregTech is present.
     */
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
     * The cover container of a GT machine or pipe (both can carry covers), or null for anything else.
     */
    @Nullable
    public static ICoverable coverContainerOf(BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity mbe) return mbe.metaMachine.getCoverContainer();
        if (be instanceof PipeBlockEntity<?, ?> pipe) return pipe.getCoverContainer();
        return null;
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

    /**
     * True if this machine is an ME stocking input bus/hatch — its item/fluid handler is backed by the
     * AE2 network, not local storage. The build's removal refund must NOT drain it, or removing one
     * would siphon the entire network into the player. Identified by GTCEu's {@link IMEStockingPart}.
     */
    public static boolean isStockingPart(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity mbe && mbe.metaMachine instanceof IMEStockingPart;
    }

    /**
     * True if this is any ME hatch/bus (input/output/stocking/pattern buffer) — its handler fronts the network.
     */
    public static boolean isMEPart(BlockEntity be) {
        return be instanceof MetaMachineBlockEntity mbe && mbe.metaMachine instanceof IGridConnectedMachine;
    }

    /**
     * Combined {@code ITEM_HANDLER} slot indices the generic inventory capture must skip for a GT
     * machine: an ME hatch/bus fronts the AE2 network, not local storage (skip everything), and a circuit slot is
     * config, not storage — the circuit is already restored for free from the semantic {@code "circuit"}
     * int, so counting/charging it again is wrong. Returns {@code null} when nothing should be skipped.
     */
    @Nullable
    public static IntPredicate excludedInventorySlots(BlockEntity be) {
        if (isMEPart(be)) return i -> true; // network-backed handler — never capture its contents
        if (!(be instanceof MetaMachineBlockEntity mbe)) return null;
        MetaMachine m = mbe.metaMachine;
        if (!(m instanceof IHasCircuitSlot cm) || !cm.isCircuitSlotEnabled()) return null;

        int start = -1, end = -1, offset = 0;
        for (MachineTrait t : m.getTraits()) {
            if (!(t instanceof IItemHandlerModifiable h) || !t.hasCapability(null)) continue;
            int slots = h.getSlots();
            if (t == cm.getCircuitInventory()) {
                start = offset;
                end = offset + slots;
            }
            offset += slots;
        }
        if (start < 0) return null;
        final int s = start, e = end;
        return i -> i >= s && i < e;
    }

    /**
     * True if {@code be} is a GTCEu ME Pattern Buffer.
     */
    public static boolean isPatternBuffer(@Nullable BlockEntity be) {
        if (be instanceof MetaMachineBlockEntity mbe) {
            var m = mbe.metaMachine;
            return m instanceof MEBusPartMachine && m instanceof ICraftingProvider && m instanceof PatternContainer && m instanceof IDataStickInteractable;
        }
        return false;
    }

    /**
     * The ME Pattern Buffer Proxy block's state for the smart-copy Pattern Buffer→proxy swap, carrying the
     * captured buffer's front facing so the proxy lands oriented like the original.
     * Tries its best to find a matching proxy, falling back to the buffer's state if no match is found.
     */
    public static BlockState proxyState(BlockState bufferState) {
        var bufferId = ForgeRegistries.BLOCKS.getKey(bufferState.getBlock());
        var proxyId = bufferId.withSuffix("_proxy");
        if (!ForgeRegistries.BLOCKS.containsKey(proxyId)) return bufferState;
        BlockState proxy = ForgeRegistries.BLOCKS.getValue(proxyId).defaultBlockState();
        if (proxy.getBlock() instanceof MetaMachineBlock pb) {
            RotationState rs = pb.getRotationState();
            if (rs != RotationState.NONE && bufferState.hasProperty(rs.property) && proxy.hasProperty(rs.property)) {
                proxy = proxy.setValue(rs.property, bufferState.getValue(rs.property));
            }
        }
        return proxy;
    }

    /**
     * Smart-copy result for a captured ME Pattern Buffer: a placed Pattern Buffer Proxy that is
     * linked back to the original buffer's world position. COPY leaves the source buffer in place, so
     * every pasted proxy slaves to it. The proxy block itself is the charged item,
     * this analysis only establishes the link on applying.
     */
    public static class PatternBufferProxyAnalysis implements ITileAnalysis {
        private final BlockPos master;

        public PatternBufferProxyAnalysis(BlockPos master) {
            this.master = master;
        }

        @Override
        public boolean apply(BlockApplyContext ctx) {
            BlockEntity be = ctx.getBlockEntity();
            if (be instanceof MetaMachineBlockEntity mbe) {
                if (mbe.metaMachine instanceof MEPatternBufferProxyPartMachine proxy) {
                    proxy.setBuffer(master);
                    be.setChanged();
                    if (needsClientSync(be)) scheduleClientSync(ctx.level(), ctx.pos());
                } else if (Mods.BGTAE2 && mbe.metaMachine instanceof ExtendMEPatternBufferProxyPartMachine proxy) {
                    // mod does not extend the base proxy...
                    proxy.setBuffer(master);
                    be.setChanged();
                    if (needsClientSync(be)) scheduleClientSync(ctx.level(), ctx.pos());
                }
            }
            return true;
        }

        @Override
        public void collectRequiredItems(RequiredItems out) {}

        @Override
        public void transform(MMTransform transform) {} // master is absolute; the source buffer doesn't move

        @Override
        public ITileAnalysis copy() {
            return new PatternBufferProxyAnalysis(master);
        }
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

    /**
     * Returns all cover items that would need to be supplied to fully reproduce this machine's covers.
     */
    public static List<ItemStack> captureCovers(BlockEntity be) {
        ICoverable covers = coverContainerOf(be);
        if (covers == null) return List.of();
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
        ICoverable covers = coverContainerOf(be);
        if (covers == null) return;
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
        ICoverable covers = coverContainerOf(be);
        if (covers == null) return List.of();
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

    /**
     * {@link IElectricItem} backed by the manipulator's own NBT charge (shared with the build loop).
     */
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
     * Semantic copy of a GregTech machine or pipe. Instead of round-tripping a whitelist of raw NBT keys,
     * this reads the player-facing settings through GregTech's own API at capture (paint, auto-output,
     * voiding, muffled, circuit, working-enabled, recipe type, distinct, batch, covers, ME-part AE config,
     * plus the machine's own {@code copyConfig}) and re-applies them with the matching setters on the live
     * placed machine — the same path GregTech's "memory card" tool uses, so machine-specific logic and the
     * managed-field client sync run correctly. The legacy whitelist NBT is still captured as
     * {@link #previewNbt}: it feeds the ghost preview (covers/paint) and a defensive fallback if the
     * semantic path can't run. Facing lives in the block state and is handled by {@code BuildGenerator}.
     */
    public static class GTMachineAnalysis implements ITileAnalysis {
        /**
         * Legacy whitelist kept only for the ghost preview + the fallback restore (not the primary apply).
         */
        private static final String[] ANALYSIS_KEYS = {
                "ownerUUID", "paintingColor", "cover", "isFlipped",
                "workingMode", "voidingMode", "batchEnabled", "isWorkingEnabled", "workingEnabled",
                "isMuffled", "isDistinct", "currentParallel", "durationMultiplier", "circuitInventory",
                "activeRecipeType", "outputFacingItems", "outputFacingFluids", "inputFacingItems",
                "inputFacingFluids", "autoOutputItems", "autoOutputFluids",
                "allowInputFromOutputSideItems", "allowInputFromOutputSideFluids",
                "autoPull", "ticksPerCycle", "minStackSize",
        };

        /**
         * Semantic settings, captured via getters and re-applied via setters (MM's own stable keys).
         */
        private CompoundTag semantic = new CompoundTag();
        /**
         * Cover container config (per-side {@code {id,item,data}}) from {@code ICoverable.copyConfig}.
         */
        private CompoundTag coverConfig = new CompoundTag();
        /**
         * ME bus/hatch AE config, copied through the data-stick API, or null if not applicable.
         */
        @Nullable
        private CompoundTag dataStick;
        /**
         * Cover items needed to reproduce the covers, for the required-items tally.
         */
        @Nullable
        private List<ItemStack> coverItems;
        /**
         * Legacy whitelist NBT slice — powers the ghost preview and the defensive apply fallback.
         */
        private CompoundTag previewNbt = new CompoundTag();

        /**
         * Captures a machine/pipe's config semantically (+ the preview/fallback slice), or null otherwise.
         */
        @Nullable
        public static GTMachineAnalysis analyze(BlockEntity be) {
            if (!(be instanceof MetaMachineBlockEntity) && !(be instanceof PipeBlockEntity)) return null;
            GTMachineAnalysis a = new GTMachineAnalysis();
            a.previewNbt = captureWhitelist(be);
            a.coverItems = captureCovers(be);
            ICoverable covers = coverContainerOf(be);
            if (covers != null) a.coverConfig = covers.copyConfig(new CompoundTag());
            if (be instanceof MetaMachineBlockEntity mbe) {
                MetaMachine machine = mbe.metaMachine;
                a.semantic = captureMachineConfig(machine);
                if (!isPatternBufferFamily(machine) && machine instanceof IDataStickInteractable dsi) {
                    a.dataStick = captureDataStick(dsi, be);
                }
            } else if (be instanceof PipeBlockEntity<?, ?> pipe) {
                a.semantic = capturePipeConfig(pipe);
            }
            return a;
        }

        /**
         * Rebuilds an analysis from previously persisted NBT (cables mode; see {@link #toNbt}).
         */
        public static GTMachineAnalysis fromConfig(CompoundTag config) {
            GTMachineAnalysis a = new GTMachineAnalysis();
            if (config.contains("semantic")) a.semantic = config.getCompound("semantic").copy();
            if (config.contains("coverConfig")) a.coverConfig = config.getCompound("coverConfig").copy();
            if (config.contains("dataStick")) a.dataStick = config.getCompound("dataStick").copy();
            if (config.contains("preview")) a.previewNbt = config.getCompound("preview").copy();
            return a;
        }

        /**
         * A reduced analysis keeping only a pipe/machine's paint color (cables mode), or null if unpainted.
         */
        @Nullable
        public GTMachineAnalysis colorOnly() {
            if (!semantic.contains("paint")) return null;
            GTMachineAnalysis a = new GTMachineAnalysis();
            a.semantic.putInt("paint", semantic.getInt("paint"));
            // Carry the block id so BuildGenerator.attachCableAnalysis can route the persisted tag.
            if (previewNbt.contains("id")) a.previewNbt.putString("id", previewNbt.getString("id"));
            return a;
        }

        /**
         * Compact, self-describing NBT for persisting a cables-mode analysis on the tool.
         */
        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.put("semantic", semantic.copy());
            if (!coverConfig.isEmpty()) t.put("coverConfig", coverConfig.copy());
            if (dataStick != null) t.put("dataStick", dataStick.copy());
            if (!previewNbt.isEmpty()) t.put("preview", previewNbt.copy());
            // Surface the block id at top level so attachCableAnalysis can route on it.
            if (previewNbt.contains("id")) t.putString("id", previewNbt.getString("id"));
            return t;
        }

        @Override
        public boolean apply(BlockApplyContext ctx) {
            BlockEntity be = ctx.getBlockEntity();
            try {
                if (be instanceof MetaMachineBlockEntity mbe) {
                    applyMachine(mbe.metaMachine, be, ctx);
                } else if (be instanceof PipeBlockEntity<?, ?> pipe) {
                    applyPipe(pipe, be, ctx);
                } else {
                    return applyFallback(ctx); // placed BE isn't a live GT machine/pipe — restore from NBT
                }
                be.setChanged();
                if (needsClientSync(be)) scheduleClientSync(ctx.level(), ctx.pos());
                return true;
            } catch (Exception e) {
                // The semantic path failed (unexpected machine shape, API drift); restore from the
                // captured NBT so the paste still carries its config.
                return applyFallback(ctx);
            }
        }

        private void applyMachine(MetaMachine m, BlockEntity be, BlockApplyContext ctx) {
            ServerPlayer player = serverPlayer(ctx);
            if (semantic.contains("paint")) m.setPaintingColor(semantic.getInt("paint"));
            if (m instanceof IAutoOutputItem ao) {
                if (semantic.contains("outItemSide"))
                    ao.setOutputFacingItems(Direction.byName(semantic.getString("outItemSide")));
                if (semantic.contains("outItemAuto")) ao.setAutoOutputItems(semantic.getBoolean("outItemAuto"));
                if (semantic.contains("outItemAllowIn"))
                    ao.setAllowInputFromOutputSideItems(semantic.getBoolean("outItemAllowIn"));
            }
            if (m instanceof IAutoOutputFluid af) {
                if (semantic.contains("outFluidSide"))
                    af.setOutputFacingFluids(Direction.byName(semantic.getString("outFluidSide")));
                if (semantic.contains("outFluidAuto")) af.setAutoOutputFluids(semantic.getBoolean("outFluidAuto"));
                if (semantic.contains("outFluidAllowIn"))
                    af.setAllowInputFromOutputSideFluids(semantic.getBoolean("outFluidAllowIn"));
            }
            if (m instanceof IMufflableMachine mm && semantic.contains("muffled"))
                mm.setMuffled(semantic.getBoolean("muffled"));
            if (m instanceof IHasCircuitSlot cm && semantic.contains("circuit"))
                cm.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(semantic.getInt("circuit")));
            if (m instanceof IControllable ic && semantic.contains("workingEnabled"))
                ic.setWorkingEnabled(semantic.getBoolean("workingEnabled"));
            if (m instanceof IVoidable iv && semantic.contains("voiding"))
                iv.setVoidingMode(voidingMode(semantic.getInt("voiding")));
            if (m instanceof IRecipeLogicMachine rlm && semantic.contains("recipeType"))
                rlm.setActiveRecipeType(semantic.getInt("recipeType"));
            if (m instanceof IDistinctPart dp && semantic.contains("distinct"))
                dp.setDistinct(semantic.getBoolean("distinct"));
            if (m instanceof IMultiController mc && semantic.contains("batch"))
                mc.setBatchEnabled(semantic.getBoolean("batch"));
            if (m instanceof IMEStockingPart sp) {
                if (semantic.contains("minStackSize")) sp.setMinStackSize(semantic.getInt("minStackSize"));
                if (semantic.contains("ticksPerCycle")) sp.setTicksPerCycle(semantic.getInt("ticksPerCycle"));
            }
            // Covers: place them (unpaid), then charge each via the shared filter (removes the unaffordable).
            m.getCoverContainer().pasteConfig(player, coverConfig);
            filterCovers(be, ctx.inv());
            // ME bus/hatch AE config via the data-stick API (FakePlayer to suppress GregTech's chat message).
            if (dataStick != null && m instanceof IDataStickInteractable dsi && !isPatternBufferFamily(m)) {
                applyDataStick(dsi, dataStick, ctx.level());
            }
            // Machine-specific catch-all (GregTech-authored; usually empty for stock machines).
            if (semantic.contains("machineConfig")) m.pasteConfig(player, semantic.getCompound("machineConfig"));
            // A fresh copy belongs to the player pasting it, like a normally placed machine.
            if (ctx.player() != null) m.setOwnerUUID(ctx.player().getUUID());
            if (m instanceof IGridConnectedMachine gm) {
                // An ME part's AE2 node is a one-shot ManagedGridNode, so onUnload()/onLoad() would destroy a live
                // node on re-paste and recreate throws. Let the BE load handle it; only reinit covers + ME status.
                reinitCovers(be);
                refreshGridNode(gm);
            } else {
                m.onUnload();
                m.onLoad();
            }
        }

        private void applyPipe(PipeBlockEntity<?, ?> pipe, BlockEntity be, BlockApplyContext ctx) {
            if (semantic.contains("paint")) pipe.setPaintingColor(semantic.getInt("paint"));
            if (semantic.contains("connections")) {
                int connections = semantic.getInt("connections");
                for (Direction dir : Direction.values()) {
                    if ((connections & (1 << dir.ordinal())) != 0) pipe.setConnection(dir, true, false);
                }
            }
            if (semantic.contains("blocked")) {
                int blocked = semantic.getInt("blocked");
                for (Direction dir : Direction.values()) {
                    if ((blocked & (1 << dir.ordinal())) != 0) pipe.setBlocked(dir, true);
                }
            }
            pipe.getCoverContainer().pasteConfig(serverPlayer(ctx), coverConfig);
            filterCovers(be, ctx.inv());
        }

        /**
         * Legacy whitelist restore via recreate-then-reinit; the defensive fallback only.
         */
        private boolean applyFallback(BlockApplyContext ctx) {
            if (previewNbt.isEmpty()) return true;
            ServerLevel level = ctx.level();
            BlockPos pos = ctx.pos();
            BlockState state = level.getBlockState(pos);
            CompoundTag tag = previewNbt.copy();
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
                filterCovers(be, ctx.inv());
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
            rotateDir(semantic, "outItemSide", transform);
            rotateDir(semantic, "outFluidSide", transform);
            rotateConnectionMask(semantic, "connections", transform);
            rotateConnectionMask(semantic, "blocked", transform);
            transformCoverConfig(coverConfig, transform);
            transformMachineNbt(previewNbt, transform);
        }

        @Override
        public ITileAnalysis copy() {
            GTMachineAnalysis dup = new GTMachineAnalysis();
            dup.semantic = semantic.copy();
            dup.coverConfig = coverConfig.copy();
            dup.dataStick = dataStick == null ? null : dataStick.copy();
            dup.previewNbt = previewNbt.copy();
            if (coverItems != null) {
                dup.coverItems = new ArrayList<>(coverItems.size());
                for (ItemStack s : coverItems) dup.coverItems.add(s.copy());
            }
            return dup;
        }

        @Override
        public void contributePreviewNbt(CompoundTag out) {
            for (String key : previewNbt.getAllKeys()) {
                out.put(key, previewNbt.get(key).copy());
            }
        }

        // ── capture helpers ────────────────────────────────────────────────────────

        private static CompoundTag captureWhitelist(BlockEntity be) {
            CompoundTag full = be.saveWithFullMetadata();
            CompoundTag config = new CompoundTag();
            if (full.contains("id")) config.putString("id", full.getString("id"));
            for (String key : ANALYSIS_KEYS) {
                Tag value = full.get(key);
                if (value != null) config.put(key, value.copy());
            }
            return config;
        }

        private static CompoundTag captureMachineConfig(MetaMachine m) {
            CompoundTag t = new CompoundTag();
            t.putInt("paint", m.getPaintingColor());
            if (m instanceof IAutoOutputItem ao && ao.getOutputFacingItems() != null) {
                t.putString("outItemSide", ao.getOutputFacingItems().getSerializedName());
                t.putBoolean("outItemAuto", ao.isAutoOutputItems());
                t.putBoolean("outItemAllowIn", ao.isAllowInputFromOutputSideItems());
            }
            if (m instanceof IAutoOutputFluid af && af.getOutputFacingFluids() != null) {
                t.putString("outFluidSide", af.getOutputFacingFluids().getSerializedName());
                t.putBoolean("outFluidAuto", af.isAutoOutputFluids());
                t.putBoolean("outFluidAllowIn", af.isAllowInputFromOutputSideFluids());
            }
            if (m instanceof IMufflableMachine mm) t.putBoolean("muffled", mm.isMuffled());
            if (m instanceof IHasCircuitSlot cm && cm.isCircuitSlotEnabled()) {
                var circInv = cm.getCircuitInventory();
                int circuit = 0;
                for (int i = 0; i < circInv.getSlots(); i++) {
                    if (circInv.getStackInSlot(i).isEmpty()) continue;
                    if (!IntCircuitBehaviour.isIntegratedCircuit(circInv.getStackInSlot(i))) continue;
                    circuit = IntCircuitBehaviour.getCircuitConfiguration(circInv.getStackInSlot(i));
                }
                if (circuit != 0) t.putInt("circuit", circuit);
            }
            if (m instanceof IControllable ic) t.putBoolean("workingEnabled", ic.isWorkingEnabled());
            if (m instanceof IVoidable iv) t.putInt("voiding", iv.getVoidingMode().ordinal());
            if (m instanceof IRecipeLogicMachine rlm) t.putInt("recipeType", rlm.getActiveRecipeType());
            if (m instanceof IDistinctPart dp) t.putBoolean("distinct", dp.isDistinct());
            if (m instanceof IMultiController mc) t.putBoolean("batch", mc.isBatchEnabled());
            // ME stocking bus/hatch auto-stock tuning — not carried by the data stick or copyConfig.
            if (m instanceof IMEStockingPart sp) {
                t.putInt("minStackSize", sp.getMinStackSize());
                t.putInt("ticksPerCycle", sp.getTicksPerCycle());
            }
            CompoundTag machineConfig = m.copyConfig(new CompoundTag());
            if (!machineConfig.isEmpty()) t.put("machineConfig", machineConfig);
            return t;
        }

        private static CompoundTag capturePipeConfig(PipeBlockEntity<?, ?> pipe) {
            CompoundTag t = new CompoundTag();
            t.putInt("paint", pipe.getPaintingColor());
            t.putInt("connections", pipe.getConnections());
            t.putInt("blocked", pipe.getBlockedConnections());
            return t;
        }

        @Nullable
        private static CompoundTag captureDataStick(IDataStickInteractable dsi, BlockEntity be) {
            if (!(be.getLevel() instanceof ServerLevel level)) return null;
            try {
                ItemStack carrier = GTItems.TOOL_DATA_STICK.asStack();
                dsi.onDataStickShiftUse(FakePlayerFactory.getMinecraft(level), carrier);
                CompoundTag tag = carrier.getTag();
                return tag == null || tag.isEmpty() ? null : tag.copy();
            } catch (Exception e) {
                return null; // some IDataStickInteractable wrote nothing useful; skip it
            }
        }

        // ── apply helpers ────────────────────────────────────────────────────────

        private static void applyDataStick(IDataStickInteractable dsi, CompoundTag tag, ServerLevel level) {
            try {
                ItemStack carrier = GTItems.TOOL_DATA_STICK.asStack();
                carrier.setTag(tag.copy());
                dsi.onDataStickUse(FakePlayerFactory.getMinecraft(level), carrier);
            } catch (Exception e) {
                // GregTech reads the config before sending its chat message; a failure here is the message.
            }
        }

        private static ServerPlayer serverPlayer(BlockApplyContext ctx) {
            return ctx.player() instanceof ServerPlayer sp ? sp : FakePlayerFactory.getMinecraft(ctx.level());
        }

        private static IVoidable.VoidingMode voidingMode(int ordinal) {
            IVoidable.VoidingMode[] values = IVoidable.VoidingMode.VALUES;
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IVoidable.VoidingMode.VOID_NONE;
        }

        private static boolean isPatternBufferFamily(MetaMachine m) {
            return isPatternBuffer(m) || isPatternBufferProxy(m);
        }

        private static boolean isPatternBuffer(MetaMachine m) {
            if (m instanceof MEPatternBufferPartMachine) return true;
            else if (Mods.BGTAE2) return m instanceof ExtendMEPatternBufferPartMachine;
            else return false;
        }

        private static boolean isPatternBufferProxy(MetaMachine m) {
            if (m instanceof MEPatternBufferProxyPartMachine) return true;
            else if (Mods.BGTAE2) return m instanceof ExtendMEPatternBufferProxyPartMachine;
            else return false;
        }

        /**
         * Re-applies the AE2 node's in-world settings and refreshes its online flag without destroying it.
         * While the node is still pending creation ({@code !isReady}) its init-data is unconsumed, so the
         * setters succeed and the block-entity load creates it with the right exposed side; once it's live
         * (in-place re-paste) the init-data is gone, so we leave the already-correct node alone and only
         * recompute its status.
         */
        private static void refreshGridNode(IGridConnectedMachine gm) {
            var mainNode = gm.getMainNode();
            if (mainNode == null) return;
            if (!mainNode.isReady()) {
                mainNode.setInWorldNode(true);
                mainNode.setExposedOnSides(exposedSides(gm));
            }
            gm.updateMEStatus();
        }

        /**
         * The sides an ME part exposes its node on: just its front facing, or all sides if it has none.
         */
        private static EnumSet<Direction> exposedSides(IGridConnectedMachine gm) {
            if (gm.self().hasFrontFacing()) return EnumSet.of(gm.self().getFrontFacing());
            return EnumSet.allOf(Direction.class);
        }

        // ── transform helpers ────────────────────────────────────────────────────────

        private static void rotateDir(CompoundTag tag, String key, MMTransform transform) {
            if (!tag.contains(key, Tag.TAG_STRING)) return;
            Direction d = Direction.byName(tag.getString(key));
            if (d != null) tag.putString(key, transform.apply(d).getSerializedName());
        }

        private static void rotateConnectionMask(CompoundTag tag, String key, MMTransform transform) {
            if (!tag.contains(key, Tag.TAG_INT)) return;
            int mask = tag.getInt(key);
            int rotated = 0;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) rotated |= 1 << transform.apply(dir).ordinal();
            }
            tag.putInt(key, rotated);
        }

        private static void transformCoverConfig(CompoundTag coverConfig, MMTransform transform) {
            if (coverConfig.isEmpty()) return;
            CompoundTag src = coverConfig.copy();
            for (Direction side : Direction.values()) coverConfig.remove(side.getSerializedName());
            for (Direction side : Direction.values()) {
                Tag blob = src.get(side.getSerializedName());
                if (blob == null) continue;
                Tag moved = blob.copy();
                if (moved instanceof CompoundTag c) {
                    rewriteCoverSide(c, side.ordinal(), transform.apply(side).ordinal());
                }
                coverConfig.put(transform.apply(side).getSerializedName(), moved);
            }
        }
    }

    /**
     * Single-direction auto-I/O sides stored directly in a GT machine's NBT, rotated on transform.
     */
    private static final String[] GT_FACING_KEYS = {
            "outputFacingItems", "outputFacingFluids", "inputFacingItems", "inputFacingFluids",
    };

    /**
     * Rotates/flips the orientation baked into a GT machine's NBT in place: permutes the per-side
     * cover sub-tags (and the {@code attachedSide} int inside each), rotates the auto-I/O facing keys,
     * and toggles a multiblock controller's {@code isFlipped} on a mirroring transform. No-op for NBT
     * that isn't a GregTech machine. Shared by {@link GTMachineAnalysis} (the copy preview/fallback slice)
     * and {@code RawTileAnalysis} (move), so a rotated paste behaves the same either way.
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
