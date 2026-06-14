package dev.wp.matter_manipulator.common.compat;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.lowdragmc.lowdraglib.syncdata.blockentity.IAutoSyncBlockEntity;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * GregTech soft-dependency compat. This class imports GregTech types, so it must only be loaded
 * when GregTech is present — every caller guards on {@link Mods#GTCEU}. Its Forge event handlers are
 * registered manually (not via {@code @Mod.EventBusSubscriber}) for the same reason; see
 * {@link #registerEvents()}.
 */
public class GTCompat {

    /** Registers the deferred-sync Forge event handlers. Call only when GregTech is present. */
    public static void registerEvents() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(GTCompat.class);
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
        if (event.phase != TickEvent.Phase.END || PENDING_SYNCS.isEmpty()
                || !(event.level instanceof ServerLevel level)) return;
        Iterator<PendingSync> it = PENDING_SYNCS.iterator();
        while (it.hasNext()) {
            PendingSync sync = it.next();
            if (sync.level != level) continue;
            // The block-update broadcast happens during the next tick's chunk tick;
            // flush only once a full tick has passed so the client BE exists.
            if (level.getGameTime() <= sync.queuedAt) continue;
            it.remove();
            if (level.isLoaded(sync.pos)
                    && level.getBlockEntity(sync.pos) instanceof IAutoSyncBlockEntity syncBE) {
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
     * removes the cover and gives the item back instead.
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
                // Remove the cover without dropping it, then route items through inv
                List<ItemStack> drops = new ArrayList<>();
                drops.add(coverItem);
                for (ItemStack extra : cover.getAdditionalDrops()) {
                    if (!extra.isEmpty()) drops.add(extra.copy());
                }
                covers.removeCover(false, dir, null);
                drops.forEach(inv::give);
            }
        }
    }

    /**
     * Capability provider exposing the manipulator as a GregTech electric item (EU), so it charges
     * in GT chargers up to {@code Long.MAX_VALUE}. Created only when GregTech is present; without it
     * the item exposes Forge Energy (FE) instead.
     */
    public static ICapabilityProvider createElectricItemProvider(ItemStack stack, MMTier tier) {
        return new ICapabilityProvider() {
            private final LazyOptional<IElectricItem> cap =
                    LazyOptional.of(() -> new MMElectricItem(stack, tier));

            @Override
            public @Nonnull <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
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
        public long discharge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean externally,
                              boolean simulate) {
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
     * Copy rule for GT machines: keeps the player-facing configuration and covers and drops the
     * positional/runtime state (recipe progress, stored energy, multiblock controller caches,
     * subscription timers) that a blanket NBT copy would carry to the new location and break.
     * Mirrors the field set a SpatialTools-style whitelist keeps. Facing lives in the block state,
     * so it is preserved regardless of NBT.
     */
    public static class GTCopyHandler implements CopyHandler {

        private static final String[] CONFIG_KEYS = {
            "ownerUUID", "paintingColor", "cover",
            "workingMode", "voidingMode", "batchEnabled",
            "isWorkingEnabled", "workingEnabled", "isMuffled", "isDistinct",
            "currentParallel", "durationMultiplier", "circuitInventory", "activeRecipeType",
            "outputFacingItems", "outputFacingFluids", "inputFacingItems", "inputFacingFluids",
            "autoOutputItems", "autoOutputFluids",
            "allowInputFromOutputSideItems", "allowInputFromOutputSideFluids",
        };

        @Override
        public boolean matches(BlockState state, @Nullable BlockEntity be, @Nullable CompoundTag fullTag) {
            // Pipes/cables are kept too, so their paint color (and covers) survive the copy.
            return be instanceof MetaMachineBlockEntity || be instanceof PipeBlockEntity;
        }

        @Override
        public void contributeNbt(CompoundTag full, CompoundTag out, MMInventory inv) {
            for (String key : CONFIG_KEYS) {
                Tag value = full.get(key);
                if (value != null) out.put(key, value.copy());
            }
        }

        @Override
        public void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be, CompoundTag full, MMInventory inv) {
            // The "cover" config we kept re-installs the covers; charge the player for them here.
            filterCovers(be, inv);
        }
    }
}
