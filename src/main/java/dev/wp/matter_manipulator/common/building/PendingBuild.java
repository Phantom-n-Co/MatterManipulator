package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.BlockTags;
import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.CopyHandlers;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.utils.Mods;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.MMUpgrades;
import dev.wp.matter_manipulator.common.items.manipulator.MMCapability;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTier;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.items.manipulator.RemoveMode;
import dev.wp.matter_manipulator.common.networking.MMNetwork;
import dev.wp.matter_manipulator.common.networking.PacketBuildStatus;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.*;

public class PendingBuild {

    private static final Map<UUID, PendingBuild> ACTIVE = new HashMap<>();

    private final UUID playerId;
    private final MMTier tier;
    private final Deque<PendingBlock> queue;
    private final int totalBlocks;

    /**
     * MOVING relocates existing blocks: it neither requires nor consumes items,
     * and pastes captured tile data (incl. container contents) verbatim.
     */
    private final boolean moving;

    /** Non-null in MOVING mode: source region cleared after build completes. */
    @Nullable private final BlockPos sourceMin;
    @Nullable private final BlockPos sourceMax;

    /**
     * MOVING mode: destination positions handled by this build. When the destination
     * overlaps the source box, these must not be cleared with the source region.
     */
    private final LongOpenHashSet movedPositions = new LongOpenHashSet();

    /** CABLES mode: placed GT pipes are auto-connected to their neighbours after placement. */
    private final boolean cableMode;

    private PendingBuild(UUID playerId, MMTier tier, List<PendingBlock> blocks, boolean moving,
                         @Nullable BlockPos sourceMin, @Nullable BlockPos sourceMax, boolean cableMode) {
        this.playerId = playerId;
        this.tier = tier;
        this.queue = new ArrayDeque<>(blocks);
        this.totalBlocks = blocks.size();
        this.moving = moving;
        this.sourceMin = sourceMin;
        this.sourceMax = sourceMax;
        this.cableMode = cableMode;
    }

    /**
     * Start (or replace) a build for a player.
     *
     * @param blocks     PendingBlocks whose {@code relPos} is relative to {@code anchor}
     *                   (or absolute world position when anchor = {@link BlockPos#ZERO}).
     * @param anchor     Added to each block's relPos to produce the world position.
     * @param moving     True for MOVING-mode builds (no item interaction).
     * @param sourceMin  Optional min corner to clear when build finishes (MOVING mode).
     * @param sourceMax  Optional max corner to clear when build finishes (MOVING mode).
     * @param cableMode  True for CABLES-mode builds: auto-connect placed GT pipes.
     */
    public static void start(Player player, MMTier tier, List<PendingBlock> blocks,
                              BlockPos anchor, boolean moving,
                              @Nullable BlockPos sourceMin, @Nullable BlockPos sourceMax, boolean cableMode) {
        if (blocks.isEmpty()) return;
        var translated = new ArrayList<PendingBlock>(blocks.size());
        for (var pb : blocks) {
            translated.add(new PendingBlock(anchor.offset(pb.relPos), pb.spec, pb.buildOrder));
        }
        translated.sort(Comparator.comparingInt(pb -> pb.buildOrder));
        ACTIVE.put(player.getUUID(), new PendingBuild(player.getUUID(), tier, translated, moving, sourceMin, sourceMax, cableMode));
        MMUtils.sendInfoToPlayer(player, "mm.info.building", blocks.size());
    }

    public static void cancel(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    /** Cancels and clears the player's status hints. */
    public static void cancel(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        sendStatus(player, LongList.of(), LongList.of());
    }

    private static void sendStatus(Player player, LongList errors, LongList warnings) {
        if (player instanceof ServerPlayer sp) {
            MMNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new PacketBuildStatus(errors.toLongArray(), warnings.toLongArray()));
        }
    }

    public static boolean hasActiveBuild(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    /** Called from ItemMatterManipulator.inventoryTick on the server. */
    public static void tick(Player player, ItemStack stack, MMTier tier, MMState state) {
        var build = ACTIVE.get(player.getUUID());
        if (build == null) return;
        build.doTick(player, stack, state);
    }

    private void doTick(Player player, ItemStack stack, MMState state) {
        if (!(player.level() instanceof ServerLevel level)) return;

        var cfg = state.getConfig();
        var inv = new MMInventory(player, cfg.uplink);

        // Power P2P: refill the energy buffer from the bound network before spending this batch.
        if (Mods.AE2 && cfg.uplink != null && state.hasUpgrade(MMUpgrades.POWER_P2P.bit)) {
            topUpFromNetwork(level, cfg.uplink, state);
        }

        int placed = 0;
        int shuffleCount = 0;
        boolean fullCycle = false;

        int speed = state.getPlaceSpeed(tier);

        // One aggregated teleport sound per tick batch, not per block
        int soundCount = 0;
        double soundX = 0, soundY = 0, soundZ = 0;

        while (placed < speed && !queue.isEmpty()) {
            var pb = queue.poll();

            // Skip out-of-world Y
            if (pb.relPos.getY() < level.getMinBuildHeight() || pb.relPos.getY() >= level.getMaxBuildHeight()) {
                continue;
            }

            // Defer unloaded chunks
            if (!level.isLoaded(pb.relPos)) {
                queue.addLast(pb);
                if (++shuffleCount > queue.size()) {
                    fullCycle = true;
                    break;
                }
                continue;
            }

            // Range check
            if (tier.range > 0) {
                double distSq = player.blockPosition().distSqr(pb.relPos);
                if (distSq > (long) tier.range * tier.range) continue;
            }

            var existingState = level.getBlockState(pb.relPos);

            // Skip if block is already correct (no tile data to restore)
            if (existingState.equals(pb.spec.state) && pb.spec.tileData == null) {
                if (moving) movedPositions.add(pb.relPos.asLong());
                placed++;
                continue;
            }

            // Check whether we're allowed to replace the existing block
            if (!existingState.isAir()) {
                if (!state.hasCapability(tier, MMCapability.ALLOW_REMOVING)) {
                    queue.addLast(pb);
                    if (++shuffleCount > queue.size()) {
                        fullCycle = true;
                        break;
                    }
                    continue;
                }
                if (!canReplace(level, pb.relPos, existingState, cfg.removeMode, cfg.placeMode)) {
                    queue.addLast(pb);
                    if (++shuffleCount > queue.size()) {
                        fullCycle = true;
                        break;
                    }
                    continue;
                }
            }

            // Dry-run placement check to catch blocks with unmet placement requirements
            var simulated = new SimulatedLevelReader(level, pb.relPos);
            if (!pb.spec.state.canSurvive(simulated, pb.relPos)) {
                // Dependency not met (e.g. torch neighbor), shuffle to back
                queue.addLast(pb);
                if (++shuffleCount > queue.size()) {
                    fullCycle = true;
                    break;
                }
                continue;
            }

            // Energy cost
            double hardness = Math.max(existingState.getDestroySpeed(level, pb.relPos), 0);
            double dist = player.position().distanceTo(Vec3.atCenterOf(pb.relPos));
            boolean hasTile = level.getBlockEntity(pb.relPos) != null;
            long feCost = computeFECost(hardness, dist, hasTile);

            if (!player.isCreative() && !state.hasCharge(feCost)) {
                queue.addFirst(pb);
                MMUtils.sendErrorToPlayer(player, "mm.info.error.out_of_eu");
                LongList errors = new LongArrayList();
                for (var remaining : queue) errors.add(remaining.relPos.asLong());
                sendStatus(player, errors, LongList.of());
                ACTIVE.remove(playerId);
                return;
            }

            // Item check — MOVING relocates existing blocks and needs no items
            var required = moving ? ItemStack.EMPTY : pb.spec.toStack();
            if (!required.isEmpty() && !inv.canConsume(required)) {
                queue.addLast(pb);
                if (++shuffleCount > queue.size()) {
                    MMMod.LOGGER.debug("Skipping {} — no items available", pb.relPos);
                    fullCycle = true;
                    break;
                }
                continue;
            }

            // Pay for the block before restoring tile data, so content filtering
            // can't spend the same item on the container's stored contents
            inv.consume(required);

            // Remove existing block first
            if (!existingState.isAir()) {
                // A MOVING destination inside the source box overwrites a block that is
                // itself being relocated — claiming its drops would duplicate it.
                boolean relocatedSource = moving && inSourceBox(pb.relPos);
                if (!relocatedSource && !dropBlacklisted(existingState)) {
                    var drops = Block.getDrops(existingState, level, pb.relPos, level.getBlockEntity(pb.relPos), player, stack);
                    for (var drop : drops) {
                        inv.give(drop);
                    }
                    reclaimContents(level.getBlockEntity(pb.relPos), inv);
                }
                // Remove the BE first: the block's onRemove would otherwise spill the
                // contents on the ground (they were reclaimed or relocated above).
                if (level.getBlockEntity(pb.relPos) != null) {
                    level.removeBlockEntity(pb.relPos);
                }
                level.setBlockAndUpdate(pb.relPos, Blocks.AIR.defaultBlockState());
            }

            // Place the new block
            level.setBlockAndUpdate(pb.relPos, pb.spec.state);

            // Restore tile entity data if present
            if (pb.spec.tileData != null) {
                BlockEntity be = level.getBlockEntity(pb.relPos);
                if (be != null) {
                    CompoundTag full = pb.spec.tileData;
                    // MOVING relocates a block verbatim (state + contents travel intact).
                    // COPYING fabricates a new block, so it keeps only the whitelisted
                    // configuration each handler allows — positional/runtime state is dropped.
                    CompoundTag tileTag = moving
                            ? full.copy()
                            : CopyHandlers.buildCopyTag(pb.spec.state, be, full, inv);
                    tileTag.putInt("x", pb.relPos.getX());
                    tileTag.putInt("y", pb.relPos.getY());
                    tileTag.putInt("z", pb.relPos.getZ());
                    if (Mods.GTCEU && GTCompat.isGTMachine(be)) {
                        // GT machines must initialize in chunk-load order (ctor → load →
                        // onLoad): loading NBT into a live machine leaves its subscriptions
                        // and multiblock state initialized against the pre-load data.
                        BlockEntity recreated = BlockEntity.loadStatic(pb.relPos, pb.spec.state, tileTag);
                        if (recreated != null) {
                            level.setBlockEntity(recreated);
                            be = recreated;
                        } else {
                            MMMod.LOGGER.warn("Could not recreate block entity at {} from NBT, loading in place", pb.relPos);
                            be.load(tileTag);
                        }
                    } else {
                        be.load(tileTag);
                    }
                    if (!moving) {
                        // Charge for covers / AE parts / single-slot items the handlers kept.
                        CopyHandlers.afterPaste(level, pb.relPos, be, full, inv);
                        // Refill item-handler contents from the capture, charged from inventory.
                        // AE2 contents live behind its own inventories (handled above), not the
                        // item-handler cap, so skip them to avoid double handling.
                        if (!(Mods.AE2 && AECompat.isAEBlock(be))) {
                            injectContents(be, pb.spec.contents, inv);
                        }
                        // Drain whatever the placed BE came with; refill captured amounts paid
                        // from the bound AE2 network (nothing free when unbound).
                        fillFluids(be, pb.spec.fluids, cfg.uplink, level);
                    }
                    // Covers were restored from NBT (possibly remapped to rotated sides); re-init the
                    // cover container so they render and can be opened without a manual refresh.
                    if (Mods.GTCEU) GTCompat.reinitCovers(be);
                    be.setChanged();
                    if (Mods.GTCEU && GTCompat.needsClientSync(be)) {
                        GTCompat.scheduleClientSync(level, pb.relPos);
                    }
                }
            }

            if (moving) movedPositions.add(pb.relPos.asLong());

            // Cable mode connects GT pipes to their neighbours (they don't auto-connect on place).
            if (cableMode && Mods.GTCEU) GTCompat.connectPipe(level, pb.relPos);

            state.consumeCharge(feCost);

            soundCount++;
            soundX += pb.relPos.getX() + 0.5;
            soundY += pb.relPos.getY() + 0.5;
            soundZ += pb.relPos.getZ() + 0.5;

            placed++;
            shuffleCount = 0;
        }

        if (soundCount > 0) {
            // Volume scales with distance so far-away batches stay audible
            double cx = soundX / soundCount, cy = soundY / soundCount, cz = soundZ / soundCount;
            float volume = (float) (player.position().distanceTo(new Vec3(cx, cy, cz)) / 16.0 + 1.0);
            level.playSound(null, cx, cy, cz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, volume, 1.0f);
        }

        if (queue.isEmpty()) {
            // MOVING mode: remove source blocks after all destination blocks are placed
            if (sourceMin != null && sourceMax != null) {
                clearSourceRegion(level);
            }
            ACTIVE.remove(playerId);
            MMUtils.sendInfoToPlayer(player, "mm.info.finished_placing");
            sendStatus(player, LongList.of(), LongList.of());
        } else if (fullCycle && placed == 0) {
            giveUp(player, inv);
        }
    }

    /** Reports blocks that could not be placed (red = placement failure, yellow = missing items) and stops the build. */
    private void giveUp(Player player, MMInventory inv) {
        LongList errors = new LongArrayList();
        LongList warnings = new LongArrayList();
        Map<net.minecraft.world.item.Item, Integer> missing = new LinkedHashMap<>();

        for (var pb : queue) {
            var required = moving ? ItemStack.EMPTY : pb.spec.toStack();
            if (!required.isEmpty() && !inv.canConsume(required)) {
                warnings.add(pb.relPos.asLong());
                missing.merge(required.getItem(), required.getCount(), Integer::sum);
            } else {
                errors.add(pb.relPos.asLong());
            }
        }

        MMUtils.sendErrorToPlayer(player, "mm.info.error.could_not_place", queue.size());
        if (!missing.isEmpty()) {
            MMUtils.sendWarningToPlayer(player, "mm.info.warning.could_not_find", warnings.size());
            for (var entry : missing.entrySet()) {
                MMUtils.sendWarningToPlayer(player, Component.literal("  ")
                        .append(entry.getKey().getDescription())
                        .append(" x " + entry.getValue()));
            }
        }

        sendStatus(player, errors, warnings);
        ACTIVE.remove(playerId);
    }

    /**
     * COPYING: refills a freshly placed block entity's item-handler slots from the captured
     * contents snapshot, charging the player and capping each slot at what they can supply.
     * The whitelisted load tag carries no items, so this is the sole item-restore path; it is
     * layout-independent (works for any container exposing the capability).
     */
    private static void injectContents(BlockEntity be, @Nullable ListTag snapshot, MMInventory inv) {
        if (snapshot == null || snapshot.isEmpty()) return;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (!(handler instanceof IItemHandlerModifiable modifiable)) return;
        boolean creative = inv.getPlayer().isCreative();

        for (int i = 0; i < snapshot.size(); i++) {
            CompoundTag slotTag = snapshot.getCompound(i);
            int slot = slotTag.getInt("Slot");
            ItemStack stack = ItemStack.of(slotTag);
            if (stack.isEmpty() || slot < 0 || slot >= modifiable.getSlots()) continue;

            int available = creative
                    ? stack.getCount()
                    : Math.min(stack.getCount(), inv.countInInventory(stack));
            if (available <= 0) continue;

            inv.consume(stack.copyWithCount(available));
            modifiable.setStackInSlot(slot, stack.copyWithCount(available));
        }
    }

    /**
     * Gives a replaced container's contents to the player. The block entity is removed
     * before the block afterwards, so the block's own onRemove can't also spill them.
     */
    private static void reclaimContents(@Nullable BlockEntity be, MMInventory inv) {
        if (be == null) return;
        // Machines that save their contents into the dropped item already keep them
        if (Mods.GTCEU && GTCompat.savesContentsToItem(be)) return;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) inv.give(stack.copy());
        }
    }

    /**
     * COPYING empties whatever fluids a freshly placed tank came with (they can't be granted for
     * free), then — if the manipulator is bound to an AE2 uplink — refills the captured amounts,
     * paying for them out of the network. Unbound, the tank is simply left empty.
     */
    private static void fillFluids(BlockEntity be, @Nullable ListTag snapshot,
                                   @Nullable Location uplink, ServerLevel level) {
        var handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (handler == null) return;
        // drain() removes one fluid type per call; bounded in case a handler refills itself
        for (int i = 0; i < 64; i++) {
            FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) break;
        }
        if (snapshot == null || snapshot.isEmpty()) return;
        if (!(Mods.AE2 && uplink != null)) return;
        for (int i = 0; i < snapshot.size(); i++) {
            FluidStack want = FluidStack.loadFluidStackFromNBT(snapshot.getCompound(i));
            if (want.isEmpty()) continue;
            int capacity = handler.fill(want, IFluidHandler.FluidAction.SIMULATE);
            if (capacity <= 0) continue;
            int got = AECompat.uplinkExtractFluid(level, uplink, want, Math.min(capacity, want.getAmount()));
            if (got <= 0) continue;
            FluidStack give = want.copy();
            give.setAmount(got);
            handler.fill(give, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /**
     * Power P2P: draws AE power from the bound network and converts it into the manipulator's
     * energy buffer (EU when GregTech is present, otherwise FE), capped at the tier capacity.
     */
    private void topUpFromNetwork(ServerLevel level, Location uplink, MMState state) {
        long space = tier.effectiveMaxEnergy() - state.getCharge();
        if (space <= 0) return;
        double fePerUnit = MMTier.usesEU() ? MMModConfig.AE_FE_PER_EU.get() : 1.0;
        double fePerAe = MMModConfig.AE_FE_PER_AE.get();
        double aeNeeded = (space * fePerUnit) / fePerAe;
        if (aeNeeded <= 0) return;
        double aeGot = AECompat.uplinkDrawPowerAE(level, uplink, aeNeeded);
        if (aeGot <= 0) return;
        long unitsGot = (long) (aeGot * fePerAe / fePerUnit);
        if (unitsGot > 0) {
            state.setCharge(Math.min(tier.effectiveMaxEnergy(), state.getCharge() + unitsGot));
        }
    }

    private boolean inSourceBox(BlockPos pos) {
        return sourceMin != null && sourceMax != null
                && pos.getX() >= sourceMin.getX() && pos.getX() <= sourceMax.getX()
                && pos.getY() >= sourceMin.getY() && pos.getY() <= sourceMax.getY()
                && pos.getZ() >= sourceMin.getZ() && pos.getZ() <= sourceMax.getZ();
    }

    /**
     * Returns true if the existing block may be overwritten given the current remove mode.
     * Bedrock-like blocks with hardness &lt; 0 are never allowed.
     */
    private static boolean canReplace(ServerLevel level, BlockPos pos, BlockState existing,
                                      RemoveMode removeMode, PlaceMode placeMode) {
        // Bedrock etc. are never replaceable
        if (existing.getDestroySpeed(level, pos) < 0) return false;

        // EXCHANGING: blocks were pre-filtered to the whitelist, always replace
        if (placeMode == PlaceMode.EXCHANGING) return true;

        return switch (removeMode) {
            case NONE        -> false;
            case REPLACEABLE -> existing.canBeReplaced();
            case ALL         -> true;
        };
    }

    /** MOVING mode: remove every non-air, non-bedrock block in the source region. */
    private void clearSourceRegion(ServerLevel level) {
        if (sourceMin == null || sourceMax == null) return;
        for (int x = sourceMin.getX(); x <= sourceMax.getX(); x++) {
            for (int y = sourceMin.getY(); y <= sourceMax.getY(); y++) {
                for (int z = sourceMin.getZ(); z <= sourceMax.getZ(); z++) {
                    var pos = new BlockPos(x, y, z);
                    // Destination overlapping the source box — already the moved block
                    if (movedPositions.contains(pos.asLong())) continue;
                    var state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
                        // Contents were relocated via NBT: remove the BE first so the
                        // block's onRemove can't spill (and thus duplicate) them.
                        if (level.getBlockEntity(pos) != null) {
                            level.removeBlockEntity(pos);
                        }
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** FE = 128 × √(hardness+1) × dist^1.25 × (hasTile ? 16 : 1), minimum 1. */
    public static long computeFECost(double hardness, double dist, boolean hasTile) {
        double base = 128.0 * Math.sqrt(Math.max(hardness, 0) + 1.0);
        double distMult = dist > 1.0 ? Math.pow(dist, 1.25) : 1.0;
        double tileMult = hasTile ? 16.0 : 1.0;
        return Math.max(1L, (long) (base * distMult * tileMult));
    }

    private static boolean dropBlacklisted(BlockState state) {
        return state.is(BlockTags.DROP_BLACKLISTED);
    }
}
