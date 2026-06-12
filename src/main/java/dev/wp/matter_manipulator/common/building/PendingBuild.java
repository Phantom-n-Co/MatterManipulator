package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.BlockTags;
import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.config.MMModConfig;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    private PendingBuild(UUID playerId, MMTier tier, List<PendingBlock> blocks, boolean moving,
                         @Nullable BlockPos sourceMin, @Nullable BlockPos sourceMax) {
        this.playerId = playerId;
        this.tier = tier;
        this.queue = new ArrayDeque<>(blocks);
        this.totalBlocks = blocks.size();
        this.moving = moving;
        this.sourceMin = sourceMin;
        this.sourceMax = sourceMax;
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
     */
    public static void start(Player player, MMTier tier, List<PendingBlock> blocks,
                              BlockPos anchor, boolean moving,
                              @Nullable BlockPos sourceMin, @Nullable BlockPos sourceMax) {
        if (blocks.isEmpty()) return;
        var translated = new ArrayList<PendingBlock>(blocks.size());
        for (var pb : blocks) {
            translated.add(new PendingBlock(anchor.offset(pb.relPos), pb.spec, pb.buildOrder));
        }
        translated.sort(Comparator.comparingInt(pb -> pb.buildOrder));
        ACTIVE.put(player.getUUID(), new PendingBuild(player.getUUID(), tier, translated, moving, sourceMin, sourceMax));
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

        var inv = new MMInventory(player);
        var cfg = state.getConfig();
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
                if (!dropBlacklisted(existingState)) {
                    var drops = Block.getDrops(existingState, level, pb.relPos, level.getBlockEntity(pb.relPos), player, stack);
                    for (var drop : drops) {
                        inv.give(drop);
                    }
                }
                level.setBlockAndUpdate(pb.relPos, Blocks.AIR.defaultBlockState());
            }

            // Place the new block
            level.setBlockAndUpdate(pb.relPos, pb.spec.state);

            // Restore tile entity data if present
            if (pb.spec.tileData != null) {
                BlockEntity be = level.getBlockEntity(pb.relPos);
                if (be != null) {
                    var tileTag = pb.spec.tileData.copy();
                    // Copying must not duplicate items: stored contents are only pasted
                    // to the extent the player can actually supply them. Containers with
                    // an item handler are filtered through it after loading (works for
                    // any NBT layout); the rest get known vanilla item tags stripped.
                    boolean viaHandler = !moving && hasItemHandler(be);
                    if (!moving && !viaHandler) {
                        filterStoredItems(tileTag, inv);
                    }
                    tileTag.putInt("x", pb.relPos.getX());
                    tileTag.putInt("y", pb.relPos.getY());
                    tileTag.putInt("z", pb.relPos.getZ());
                    be.load(tileTag);
                    if (viaHandler) {
                        filterHandlerContents(be, inv);
                    }
                    be.setChanged();
                }
            }

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

    private static boolean hasItemHandler(BlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    /**
     * Shrinks every item-handler slot to the count the player can supply, consuming
     * those items. Runs after the captured tag is loaded into the block entity, so it
     * works for any container exposing the capability regardless of its NBT layout.
     */
    private static void filterHandlerContents(BlockEntity be, MMInventory inv) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        if (handler == null) return;
        boolean creative = inv.getPlayer().isCreative();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            int available = creative
                    ? stack.getCount()
                    : Math.min(stack.getCount(), inv.countInInventory(stack));
            if (available > 0) {
                inv.consume(stack.copyWithCount(available));
            }

            int excess = stack.getCount() - available;
            if (excess > 0) {
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(slot, available <= 0 ? ItemStack.EMPTY : stack.copyWithCount(available));
                } else {
                    handler.extractItem(slot, excess, false);
                }
            }
        }
    }

    /**
     * NBT fallback for block entities without an item handler: removes stored items
     * the player cannot supply from the captured tile tag, consuming the ones they
     * can. Covers the standard container "Items" list plus common single-item slots
     * (lectern books, jukebox records).
     */
    private static void filterStoredItems(CompoundTag tileTag, MMInventory inv) {
        boolean creative = inv.getPlayer().isCreative();

        if (tileTag.contains("Items", Tag.TAG_LIST)) {
            ListTag items = tileTag.getList("Items", Tag.TAG_COMPOUND);
            ListTag kept = new ListTag();
            for (int i = 0; i < items.size(); i++) {
                CompoundTag slotTag = items.getCompound(i);
                ItemStack stack = ItemStack.of(slotTag);
                if (stack.isEmpty()) continue;

                int available = creative
                        ? stack.getCount()
                        : Math.min(stack.getCount(), inv.countInInventory(stack));
                if (available <= 0) continue;

                inv.consume(stack.copyWithCount(available));
                CompoundTag keptTag = slotTag.copy();
                keptTag.putByte("Count", (byte) available);
                kept.add(keptTag);
            }
            tileTag.put("Items", kept);
        }

        for (String key : new String[]{"Book", "RecordItem"}) {
            if (!tileTag.contains(key, Tag.TAG_COMPOUND)) continue;
            ItemStack stack = ItemStack.of(tileTag.getCompound(key));
            if (!stack.isEmpty() && inv.canConsume(stack)) {
                inv.consume(stack);
            } else {
                tileTag.remove(key);
                // A lectern without a book must not claim to have one
                if (key.equals("Book")) tileTag.remove("Page");
            }
        }
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
                    var state = level.getBlockState(pos);
                    if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
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
