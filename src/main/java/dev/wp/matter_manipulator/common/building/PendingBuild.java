package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.BlockTags;
import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
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
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import static dev.wp.matter_manipulator.MMMod.MODID;

/**
 * The per-player active build. {@link #doTick} mirrors the original mod's two-phase
 * {@code PendingBuild.tryPlaceBlocks}: it first selects a homogeneous batch of one block type that
 * passes protection / removability / dependency / power checks, then bulk-consumes the items and
 * places what the player can afford (re-queuing the rest). Block removal refunds the existing block
 * through {@link #removeBlock}, and captured tile state is reproduced via the analyses
 * ({@link PendingBlock#apply}). MOVING rides the same loop via the {@code moving} flag.
 */
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
    @Nullable
    private final BlockPos sourceMin;

    @Nullable
    private final BlockPos sourceMax;

    /**
     * MOVING mode: destination positions handled by this build. When the destination
     * overlaps the source box, these must not be cleared with the source region.
     */
    private final LongOpenHashSet movedPositions = new LongOpenHashSet();

    /** CABLES mode: placed GT pipes are auto-connected to their neighbours after placement. */
    private final boolean cableMode;

    /** Block protection warns the player only once per build (mirrors the original's flag). */
    private boolean warnedProtected = false;

    /**
     * Build-scoped ExtendedAE wireless frequency remap (original→fresh). Shared across the whole build
     * so both copies of a connector pair settle on one new frequency and link to each other.
     */
    private final Map<Long, Long> wirelessRemap = new HashMap<>();

    private PendingBuild(
            UUID playerId,
            MMTier tier,
            List<PendingBlock> blocks,
            boolean moving,
            @Nullable BlockPos sourceMin,
            @Nullable BlockPos sourceMax,
            boolean cableMode) {
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
     * @param blocks    PendingBlocks whose {@code relPos} is relative to {@code anchor}
     *                  (or absolute world position when anchor = {@link BlockPos#ZERO}).
     * @param anchor    Added to each block's relPos to produce the world position.
     * @param moving    True for MOVING-mode builds (no item interaction).
     * @param sourceMin Optional min corner to clear when build finishes (MOVING mode).
     * @param sourceMax Optional max corner to clear when build finishes (MOVING mode).
     * @param cableMode True for CABLES-mode builds: auto-connect placed GT pipes.
     */
    public static void start(
            Player player,
            MMTier tier,
            List<PendingBlock> blocks,
            BlockPos anchor,
            boolean moving,
            @Nullable BlockPos sourceMin,
            @Nullable BlockPos sourceMax,
            boolean cableMode) {
        if (blocks.isEmpty()) return;
        var translated = new ArrayList<PendingBlock>(blocks.size());
        for (var pb : blocks) {
            translated.add(pb.at(anchor.offset(pb.relPos)));
        }
        // Sort by build order, THEN by block state, then by position. Grouping identical states
        // adjacently is what lets the two-phase loop's homogeneous batch fill the whole place speed;
        // sorting by build order alone leaves mixed states interleaved, so each batch (and thus each
        // tick) places only a tiny run. Mirrors the original's (buildOrder, spec, coord) comparator.
        translated.sort(Comparator.comparingInt((PendingBlock pb) -> pb.buildOrder)
                .thenComparingInt(pb -> pb.spec.state.hashCode())
                .thenComparingLong(pb -> pb.relPos.asLong()));
        ACTIVE.put(
                player.getUUID(),
                new PendingBuild(player.getUUID(), tier, translated, moving, sourceMin, sourceMax, cableMode));
        MMUtils.sendInfoToPlayer(player, MODID + ".info.building", blocks.size());
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
            MMNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
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
        boolean powerEff = state.hasUpgrade(MMUpgrades.POWER_EFF.bit);

        // Power P2P: refill the energy buffer from the bound network before spending this batch.
        if (Mods.AE2 && cfg.uplink != null && state.hasUpgrade(MMUpgrades.POWER_P2P.bit)) {
            topUpFromNetwork(level, cfg.uplink, state);
        }

        int speed = state.getPlaceSpeed(tier);

        // ── Phase A: pick a homogeneous batch of one block type, charging nothing yet ──
        // Power is verified cumulatively here but only spent per actually-placed block in Phase B, so a
        // block we select but can't place (out of items) doesn't drain energy.
        List<PendingBlock> toPlace = new ArrayList<>(speed);
        LongList costs = new LongArrayList(speed);
        long batchPower = 0;
        int shuffleCount = 0;
        boolean fullCycle = false;
        boolean outOfPower = false;

        while (toPlace.size() < speed && !queue.isEmpty()) {
            var next = queue.peekFirst();
            BlockPos pos = next.relPos;

            // Out-of-world Y → drop
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
                queue.removeFirst();
                continue;
            }

            // Unloaded chunk → defer (re-queue); terminating via shuffleCount
            if (!level.isLoaded(pos)) {
                queue.addLast(queue.removeFirst());
                if (++shuffleCount > queue.size()) {
                    fullCycle = true;
                    break;
                }
                continue;
            }

            // Block protection (placement) → drop + warn once
            if (!BuildProtection.canPlace(level, pos, player)) {
                warnProtectedOnce(player);
                queue.removeFirst();
                continue;
            }

            // A batch is one block type, so stop selecting once the spec changes.
            if (!toPlace.isEmpty() && !next.spec.state.equals(toPlace.get(0).spec.state)) break;

            BlockState existing = level.getBlockState(pos);

            // Nothing to do for air-on-air
            if (next.spec.isAir() && existing.isAir()) {
                queue.removeFirst();
                continue;
            }

            // In-place reconfigure: the block is already what we want, so just (re)apply its tile
            // state, charging only the analysis-installed items (no block item, no remove/replace, no
            // EU). This is the original's areEquivalent branch and keeps exchange/config from
            // needlessly destroying a matching block.
            if (existing.equals(next.spec.state)) {
                var block = queue.removeFirst();
                if (!block.analyses.isEmpty()) block.apply(new BlockApplyContext(level, pos, inv, wirelessRemap));
                if (moving) movedPositions.add(pos.asLong());
                continue;
            }

            // The existing block must be removable
            if (!existing.isAir()) {
                if (!state.hasCapability(tier, MMCapability.ALLOW_REMOVING)) {
                    queue.removeFirst();
                    continue;
                }
                if (!canReplace(level, pos, existing, cfg.removeMode, cfg.placeMode)) {
                    queue.addLast(queue.removeFirst());
                    if (++shuffleCount > queue.size()) {
                        fullCycle = true;
                        break;
                    }
                    continue;
                }
                if (!BuildProtection.canBreak(level, pos, player)) {
                    warnProtectedOnce(player);
                    queue.removeFirst();
                    continue;
                }
            }

            // Placement dependency (e.g. a torch needs a wall)
            if (!next.spec.state.canSurvive(new SimulatedLevelReader(level, pos), pos)) {
                queue.addLast(queue.removeFirst());
                if (++shuffleCount > queue.size()) {
                    fullCycle = true;
                    break;
                }
                continue;
            }

            long cost = energyCost(level, pos, existing, next, player, powerEff);
            if (!player.isCreative() && !state.hasCharge(batchPower + cost)) {
                outOfPower = true;
                break;
            }

            toPlace.add(queue.removeFirst());
            costs.add(cost);
            batchPower += cost;
            shuffleCount = 0;
        }

        // Nothing to place this tick
        if (toPlace.isEmpty()) {
            if (outOfPower) {
                MMUtils.sendErrorToPlayer(player, MODID + ".info.error.out_of_eu"); // pause; build persists
            } else if (queue.isEmpty()) {
                finish(player, level);
            } else if (fullCycle) {
                giveUp(player, inv);
            }
            return;
        }

        // ── Bulk item consume (mirrors the original CONSUME_PARTIAL + refund-on-shortfall) ──
        // MOVING and item-less/free blocks (air, fluids, AE cable bus) pay nothing here; AE parts are
        // charged by the analysis on apply.
        PendingBlock first = toPlace.get(0);
        ItemStack perBlock = moving ? ItemStack.EMPTY : first.spec.toStack();
        int affordable = toPlace.size();
        if (!perBlock.isEmpty()) {
            int perCount = perBlock.getCount();
            int paid = inv.consumeUpTo(perBlock, toPlace.size() * perCount);
            affordable = paid / perCount;
            int remainder = paid - affordable * perCount;
            if (remainder > 0) inv.give(perBlock.copyWithCount(remainder));
            if (affordable < toPlace.size()) {
                MMUtils.sendWarningToPlayer(player, MODID + ".info.warning.could_not_find", toPlace.size() - affordable);
            }
        }

        // ── Phase B: place the blocks we can afford ──
        int placed = 0;
        int soundCount = 0;
        double soundX = 0, soundY = 0, soundZ = 0;

        int i = 0;
        for (; i < affordable; i++) {
            PendingBlock pb = toPlace.get(i);
            BlockPos pos = pb.relPos;
            BlockState existing = level.getBlockState(pos);

            if (!existing.isAir()) {
                // A MOVING destination inside the source box is a block being relocated; refunding it
                // would duplicate it, so removeBlock clears it rather than refunding.
                boolean relocatedSource = moving && inSourceBox(pos);
                removeBlock(level, pos, existing, player, stack, inv, relocatedSource);
            }

            if (!pb.spec.isAir()) {
                // The BlockState is authoritative (it already carries facing/orientation), so we set it
                // directly rather than via the item's placement — calling setPlacedBy would re-orient
                // directional and GT machines toward the player and break copy rotation.
                level.setBlockAndUpdate(pos, pb.spec.state);
                if (!pb.analyses.isEmpty()) pb.apply(new BlockApplyContext(level, pos, inv, wirelessRemap));
            }

            if (moving) movedPositions.add(pos.asLong());
            // Cable mode connects GT pipes to their neighbours (they don't auto-connect on place).
            if (cableMode && Mods.GTCEU) GTCompat.connectPipe(level, pos);

            if (!player.isCreative()) state.consumeCharge(costs.getLong(i));

            soundCount++;
            soundX += pos.getX() + 0.5;
            soundY += pos.getY() + 0.5;
            soundZ += pos.getZ() + 0.5;
            placed++;
        }

        // Re-queue blocks selected but not placed (item shortfall) for a later tick
        for (; i < toPlace.size(); i++) queue.addFirst(toPlace.get(i));

        if (soundCount > 0) {
            // Volume scales with distance so far-away batches stay audible
            double cx = soundX / soundCount, cy = soundY / soundCount, cz = soundZ / soundCount;
            float volume = (float) (player.position().distanceTo(new Vec3(cx, cy, cz)) / 16.0 + 1.0);
            level.playSound(null, cx, cy, cz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, volume, 1.0f);
        }

        if (queue.isEmpty()) {
            finish(player, level);
        } else if (placed > 0) {
            // MOVING reports incremental progress like the original; placement stays quiet per-tick.
            if (moving) MMUtils.sendInfoToPlayer(player, MODID + ".info.process_move", placed, queue.size());
        } else if (outOfPower) {
            MMUtils.sendErrorToPlayer(player, MODID + ".info.error.out_of_eu"); // pause; build persists
        } else {
            // No progress and not a power pause (blocked placement or missing items) → report and end.
            giveUp(player, inv);
        }
    }

    /** Ends the build: clears the MOVING source region, drops the active entry, and reports completion. */
    private void finish(Player player, ServerLevel level) {
        if (sourceMin != null && sourceMax != null) {
            clearSourceRegion(level, player);
        }
        ACTIVE.remove(playerId);
        MMUtils.sendInfoToPlayer(player, moving ? MODID + ".info.finished_move" : MODID + ".info.finished_placing");
        sendStatus(player, LongList.of(), LongList.of());
    }

    private void warnProtectedOnce(Player player) {
        if (!warnedProtected) {
            MMUtils.sendWarningToPlayer(player, MODID + ".info.warning.protected_area");
            warnedProtected = true;
        }
    }

    /** Reports blocks that could not be placed (red = placement failure, yellow = missing items) and stops the build. */
    private void giveUp(Player player, MMInventory inv) {
        LongList errors = new LongArrayList();
        LongList warnings = new LongArrayList();
        Map<Item, Integer> missing = new LinkedHashMap<>();

        for (var pb : queue) {
            var required = moving ? ItemStack.EMPTY : pb.spec.toStack();
            if (!required.isEmpty() && !inv.canConsume(required)) {
                warnings.add(pb.relPos.asLong());
                missing.merge(required.getItem(), required.getCount(), Integer::sum);
            } else {
                errors.add(pb.relPos.asLong());
            }
        }

        MMUtils.sendErrorToPlayer(player, MODID + ".info.error.could_not_place", queue.size());
        if (!missing.isEmpty()) {
            MMUtils.sendWarningToPlayer(player, MODID + ".info.warning.could_not_find", warnings.size());
            for (var entry : missing.entrySet()) {
                MMUtils.sendWarningToPlayer(
                        player,
                        Component.literal("  ")
                                .append(entry.getKey().getDescription())
                                .append(" x " + entry.getValue()));
            }
        }

        sendStatus(player, errors, warnings);
        ACTIVE.remove(playerId);
    }

    /**
     * Removes the existing block, refunding it to the player. Ore and drop-blacklisted blocks are
     * voided (so the manipulator can't fortune-mine), and a MOVING block being relocated is cleared
     * rather than refunded (it would otherwise duplicate). AE2 blocks are disassembled by hand and GT
     * machines hand back their covers; everything is given through the {@link MMInventory} (network →
     * inventory → dropped). The block entity is removed before the block so its {@code onRemove} can't
     * also spill the contents we just reclaimed.
     */
    private void removeBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState existing,
            Player player,
            ItemStack tool,
            MMInventory inv,
            boolean relocatedSource) {
        BlockEntity be = level.getBlockEntity(pos);

        if (relocatedSource || dropBlacklisted(existing) || existing.is(Tags.Blocks.ORES)) {
            if (be != null) level.removeBlockEntity(pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            return;
        }

        if (Mods.AE2 && be != null && AECompat.isAEBlock(be)) {
            // AE blocks drop nothing through Block.getDrops; disassemble them by hand.
            for (ItemStack s : AECompat.dismantle(be)) inv.give(s);
        } else if (Mods.GTCEU && be != null && GTCompat.isGTMachine(be)) {
            for (ItemStack s : GTCompat.detachCovers(be)) inv.give(s); // before getDrops → no double count
            reclaimContents(be, inv); // items; self-skips machines that bake contents into their drop
            for (ItemStack s : Block.getDrops(existing, level, pos, be, player, tool)) inv.give(s);
        } else {
            reclaimContents(be, inv);
            drainTanks(be, inv);
            ItemStack item = new ItemStack(existing.getBlock());
            if (!item.isEmpty()) inv.give(item);
        }

        if (be != null) level.removeBlockEntity(pos);
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Gives a replaced container's contents to the player. The block entity is removed
     * before the block afterwards, so the block's own onRemove can't also spill them.
     */
    private static void reclaimContents(@Nullable BlockEntity be, MMInventory inv) {
        if (be == null) return;
        // Machines that save their contents into the dropped item already keep them
        if (Mods.GTCEU && GTCompat.savesContentsToItem(be)) return;
        // An ME stocking bus's handler is the network, not local storage — never reclaim it
        if (Mods.GTCEU && GTCompat.isStockingPart(be)) return;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) inv.give(stack.copy());
        }
    }

    /** Drains a replaced tank's fluids back to the player (network or, failing that, voided). */
    private static void drainTanks(@Nullable BlockEntity be, MMInventory inv) {
        if (be == null) return;
        // An ME stocking hatch's tank is the network, not local storage — never drain it
        if (Mods.GTCEU && GTCompat.isStockingPart(be)) return;
        IFluidHandler handler = be.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElse(null);
        if (handler == null) return;
        // drain() removes one fluid type per call; bounded in case a handler refills itself
        for (int i = 0; i < 1024; i++) {
            FluidStack drained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) break;
            inv.giveFluid(drained);
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
        return sourceMin != null
                && sourceMax != null
                && pos.getX() >= sourceMin.getX()
                && pos.getX() <= sourceMax.getX()
                && pos.getY() >= sourceMin.getY()
                && pos.getY() <= sourceMax.getY()
                && pos.getZ() >= sourceMin.getZ()
                && pos.getZ() <= sourceMax.getZ();
    }

    /**
     * Returns true if the existing block may be overwritten given the current remove mode.
     * Bedrock-like blocks with hardness &lt; 0 are never allowed.
     */
    private static boolean canReplace(
            ServerLevel level, BlockPos pos, BlockState existing, RemoveMode removeMode, PlaceMode placeMode) {
        // Bedrock etc. are never replaceable
        if (existing.getDestroySpeed(level, pos) < 0) return false;

        // EXCHANGING: blocks were pre-filtered to the whitelist, always replace
        if (placeMode == PlaceMode.EXCHANGING) return true;

        return switch (removeMode) {
            case NONE -> false;
            case REPLACEABLE -> existing.canBeReplaced();
            case ALL -> true;
        };
    }

    /**
     * MOVING mode: remove every non-air, non-bedrock block in the source region. A protected source
     * block is left in place (it was never captured/relocated), and a destination that overlapped the
     * source box ({@code movedPositions}) is the already-moved block, so it is skipped too.
     */
    private void clearSourceRegion(ServerLevel level, Player player) {
        if (sourceMin == null || sourceMax == null) return;
        for (int x = sourceMin.getX(); x <= sourceMax.getX(); x++) {
            for (int y = sourceMin.getY(); y <= sourceMax.getY(); y++) {
                for (int z = sourceMin.getZ(); z <= sourceMax.getZ(); z++) {
                    var pos = new BlockPos(x, y, z);
                    if (movedPositions.contains(pos.asLong())) continue;
                    var state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
                    if (!BuildProtection.canBreak(level, pos, player)) continue;
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

    /**
     * Energy for removing the existing block plus placing the new one: each is
     * {@code 128 × (1 + √hardness) × dist^1.25 × (hasTile ? 16 : 1)}, halved by the Power Efficiency
     * upgrade. Mirrors the original's per-action {@code tryConsumePower}.
     */
    private long energyCost(
            ServerLevel level, BlockPos pos, BlockState existing, PendingBlock pb, Player player, boolean powerEff) {
        double dist = player.position().distanceTo(Vec3.atCenterOf(pos));
        long cost = 0;
        if (!existing.isAir()) {
            cost += computeFECost(existing.getDestroySpeed(level, pos), dist, level.getBlockEntity(pos) != null);
        }
        if (!pb.spec.isAir()) {
            cost += computeFECost(pb.spec.state.getDestroySpeed(level, pos), dist, pb.spec.state.hasBlockEntity());
        }
        if (powerEff) cost = Math.max(1, cost / 2);
        return cost;
    }

    /** FE = 128 × (1 + √hardness) × dist^1.25 × (hasTile ? 16 : 1), minimum 1. */
    public static long computeFECost(double hardness, double dist, boolean hasTile) {
        double base = 128.0 * (1.0 + Math.sqrt(Math.max(hardness, 0)));
        double distMult = dist > 1.0 ? Math.pow(dist, 1.25) : 1.0;
        double tileMult = hasTile ? 16.0 : 1.0;
        return Math.max(1L, (long) (base * distMult * tileMult));
    }

    private static boolean dropBlacklisted(BlockState state) {
        return state.is(BlockTags.DROP_BLACKLISTED);
    }
}
