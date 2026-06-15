package dev.wp.matter_manipulator.common.compat.ae;

import dev.wp.matter_manipulator.MMMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import javax.annotation.Nullable;

/**
 * Host-agnostic AE2 bridge for the Matter Manipulator uplink. It exposes a network's storage,
 * energy, and autocrafting to a bound manipulator, and stores per-player <b>plan patterns</b>:
 * processing patterns whose inputs are a build's blocks and whose output is a synthetic
 * {@link PlanTokenItem}.
 *
 * <p>Two flavours of plan:
 * <ul>
 * <li><b>Manual</b> — the pattern just sits in the network; the player requests the token from
 * their AE terminal. When crafted, the build's blocks (the inputs) land in network storage and
 * the token is emitted-then-voided so the job finishes without leaving a token item.</li>
 * <li><b>Auto</b> — the uplink itself submits a one-shot crafting job for the token (acting as an
 * {@link ICraftingRequester}) and <b>cancels it the instant the pattern is pushed</b>, so the
 * token is never produced at all; the build's blocks still land in storage. This mirrors the
 * 1.7.10 uplink's auto plans.</li>
 * </ul>
 *
 * <p>Constructed from an {@link IManagedGridNode} + host callbacks only, so the same component can
 * later be mounted on a GregTech multiblock.
 */
public class UplinkLogic {
    private static final int PATTERN_SLOTS = 36;

    private final UplinkPatternLogic logic;
    private final IManagedGridNode mainNode;
    private final PatternProviderLogicHost logicHost;
    private final IActionHost actionHost;

    @Nullable
    private final ICraftingRequester requester;

    private final Runnable onDirty;

    /** Per-slot plan state (null = empty slot); the pattern itself lives in {@code logic}'s inventory. */
    private final PlanEntry[] slots = new PlanEntry[PATTERN_SLOTS];

    private long nextPlanId = 0;

    /** Manual token outputs queued by {@link #onPatternPush} to be emitted+voided on the next tick. */
    private final List<GenericStack> pendingReturns = new ArrayList<>();

    public UplinkLogic(
            IManagedGridNode mainNode, PatternProviderLogicHost logicHost, IActionHost actionHost, Runnable onDirty) {
        this.mainNode = mainNode;
        this.logicHost = logicHost;
        this.actionHost = actionHost;
        this.requester = actionHost instanceof ICraftingRequester r ? r : null;
        this.onDirty = onDirty;
        this.logic = new UplinkPatternLogic(mainNode, logicHost, this, PATTERN_SLOTS);
    }

    public PatternProviderLogic getLogic() {
        return logic;
    }

    /** State for one stored plan slot. */
    private static final class PlanEntry {
        final UUID owner;
        final boolean auto;
        final ItemStack token; // pattern output; identifies the plan

        @Nullable
        ICraftingLink link; // auto: the live crafting job (transient + persisted)

        @Nullable
        Future<ICraftingPlan> calc; // auto: in-flight job calculation (transient)

        PlanEntry(UUID owner, boolean auto, ItemStack token) {
            this.owner = owner;
            this.auto = auto;
            this.token = token;
        }

        AEKey tokenKey() {
            return AEItemKey.of(token);
        }
    }

    // ── Network sourcing (used by the MM build via AECompat) ───────────────────────

    /** Amount of a key available in network storage. */
    public long available(AEKey key) {
        IGrid grid = grid();
        if (grid == null) return 0;
        KeyCounter counter = new KeyCounter();
        grid.getStorageService().getInventory().getAvailableStacks(counter);
        return counter.get(key);
    }

    /** Extracts up to {@code amount} of a key from network storage; returns the amount obtained. */
    public long extract(AEKey key, long amount) {
        if (amount <= 0) return 0;
        IGrid grid = grid();
        if (grid == null) return 0;
        return StorageHelper.poweredExtraction(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                key,
                amount,
                src(),
                Actionable.MODULATE);
    }

    /** Inserts into network storage; returns the amount that did not fit. */
    public long insert(AEKey key, long amount) {
        if (amount <= 0) return 0;
        IGrid grid = grid();
        if (grid == null) return amount;
        long inserted = StorageHelper.poweredInsert(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                key,
                amount,
                src(),
                Actionable.MODULATE);
        return amount - inserted;
    }

    /** Draws power from the network energy service; {@code ae} is in AE units, returns AE obtained. */
    public double drawPowerAE(double ae) {
        if (ae <= 0) return 0;
        IGrid grid = grid();
        if (grid == null) return 0;
        return grid.getEnergyService().extractAEPower(ae, Actionable.MODULATE, PowerMultiplier.ONE);
    }

    // ── Plan patterns ──────────────────────────────────────────────────────────────

    /**
     * Stores a new plan pattern for {@code owner}: inputs = the build's blocks, output = a unique
     * Plan token. {@code auto} plans are crafted once by the uplink itself; otherwise the pattern is
     * left for the player to request from their terminal. Returns false if the store is full.
     */
    public boolean addPlan(UUID owner, GenericStack[] blocks, boolean auto) {
        if (blocks.length == 0) return false;
        int slot = firstEmptySlot();
        if (slot < 0) return false;

        long total = 0;
        for (GenericStack s : blocks) if (s != null) total += s.amount();
        ItemStack token = PlanTokenItem.create(owner, nextPlanId++, total);
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                blocks, new GenericStack[] { new GenericStack(AEItemKey.of(token), 1) });

        logic.getPatternInv().setItemDirect(slot, pattern);
        slots[slot] = new PlanEntry(owner, auto, token);
        logic.updatePatterns();
        onDirty.run();
        return true;
    }

    /** Clears every plan owned by {@code owner} (cancelling any auto jobs); returns how many were removed. */
    public int clearPlan(UUID owner) {
        int cleared = 0;
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            if (slots[i] != null && owner.equals(slots[i].owner)) {
                cancelEntry(slots[i]);
                removeSlot(i);
                cleared++;
            }
        }
        if (cleared > 0) onDirty.run();
        return cleared;
    }

    /**
     * Called from {@link UplinkPatternLogic#pushPattern}: the network has crafted a plan's inputs and
     * pushes them here. The inputs (the build's blocks) always go to network storage. For an auto
     * plan we then cancel its job (the token is never produced); for a manual plan we queue the token
     * to be emitted-then-voided next tick so the player's job finishes cleanly.
     */
    void onPatternPush(IPatternDetails details, KeyCounter[] inputHolder) {
        IGrid grid = grid();
        if (grid == null) return;
        var inv = grid.getStorageService().getInventory();
        var es = grid.getEnergyService();
        for (KeyCounter counter : inputHolder) {
            for (var e : counter) {
                if (e.getKey() != null && e.getLongValue() > 0) {
                    StorageHelper.poweredInsert(es, inv, e.getKey(), e.getLongValue(), src(), Actionable.MODULATE);
                }
            }
        }

        GenericStack[] outputs = details.getOutputs();
        AEKey outKey = outputs.length > 0 && outputs[0] != null ? outputs[0].what() : null;
        int slot = outKey == null ? -1 : findSlotByToken(outKey);

        if (slot >= 0 && slots[slot].auto) {
            // Auto plan: cancel the job now — the token never needs to exist.
            cancelEntry(slots[slot]);
            removeSlot(slot);
        } else {
            // Manual plan: emit + void the token next tick so the player's craft completes.
            for (GenericStack out : outputs) {
                if (out != null && out.what() != null && out.amount() > 0) {
                    pendingReturns.add(out);
                }
            }
        }
    }

    /** Server tick: drive auto-plan crafting jobs and emit-then-void any manual token outputs. */
    public void tick() {
        IGrid grid = grid();
        if (grid != null) {
            processAutoPlans(grid);
        }
        if (!pendingReturns.isEmpty()) {
            voidPendingTokens(grid);
        }
    }

    private void processAutoPlans(IGrid grid) {
        if (requester == null) return;
        Level level = logicHost.getBlockEntity().getLevel();
        if (level == null) return;
        var crafting = grid.getCraftingService();

        for (int i = 0; i < PATTERN_SLOTS; i++) {
            PlanEntry e = slots[i];
            if (e == null || !e.auto) continue;

            // Drop the plan if its job ended on its own.
            if (e.link != null) {
                if (e.link.isCanceled() || e.link.isDone()) {
                    e.link = null;
                    removeSlot(i);
                }
                continue;
            }

            if (e.calc == null) {
                e.calc = crafting.beginCraftingCalculation(
                        level, this::src, e.tokenKey(), 1, CalculationStrategy.REPORT_MISSING_ITEMS);
                continue;
            }

            if (!e.calc.isDone()) continue;

            ICraftingPlan plan;
            try {
                plan = e.calc.get();
            } catch (Exception ex) {
                MMMod.LOGGER.debug("uplink: auto-craft calculation failed", ex);
                e.calc = null;
                notifyOwner(e.owner, level, "mm.info.error.autocraft_failed");
                removeSlot(i);
                continue;
            }
            e.calc = null;

            if (plan == null || !plan.missingItems().isEmpty()) {
                notifyOwner(e.owner, level, "mm.info.error.autocraft_failed");
                removeSlot(i);
                continue;
            }

            var result = crafting.submitJob(plan, requester, null, true, src());
            if (result != null && result.successful() && result.link() != null) {
                e.link = result.link();
                onDirty.run();
            } else {
                notifyOwner(e.owner, level, "mm.info.error.autocraft_failed");
                removeSlot(i);
            }
        }
    }

    private void voidPendingTokens(@Nullable IGrid grid) {
        if (grid == null) {
            pendingReturns.clear();
            return;
        }
        var inv = grid.getStorageService().getInventory();
        var es = grid.getEnergyService();
        for (GenericStack out : pendingReturns) {
            StorageHelper.poweredInsert(es, inv, out.what(), out.amount(), src(), Actionable.MODULATE);
            inv.extract(out.what(), out.amount(), Actionable.MODULATE, src());
        }
        pendingReturns.clear();
    }

    // ── ICraftingRequester delegation (for auto plans) ─────────────────────────────

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        ImmutableSet.Builder<ICraftingLink> jobs = ImmutableSet.builder();
        for (PlanEntry e : slots) {
            if (e != null && e.link != null) jobs.add(e.link);
        }
        return jobs.build();
    }

    /** Auto jobs are cancelled at push, so any delivery here is voided (we claim we consumed it all). */
    public long insertCraftedItems(AEKey what, long amount, Actionable mode) {
        return amount;
    }

    public void jobStateChange(ICraftingLink link) {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            PlanEntry e = slots[i];
            if (e != null && e.link == link) {
                e.link = null;
                removeSlot(i);
                return;
            }
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────────

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        CompoundTag logicTag = new CompoundTag();
        logic.writeToNBT(logicTag);
        tag.put("logic", logicTag);

        ListTag plans = new ListTag();
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            PlanEntry e = slots[i];
            if (e == null) continue;
            CompoundTag p = new CompoundTag();
            p.putInt("s", i);
            p.putUUID("u", e.owner);
            p.putBoolean("a", e.auto);
            p.put("tok", e.token.save(new CompoundTag()));
            if (e.link != null) {
                CompoundTag linkTag = new CompoundTag();
                e.link.writeToNBT(linkTag);
                p.put("link", linkTag);
            }
            plans.add(p);
        }
        tag.put("plans", plans);
        tag.putLong("nextId", nextPlanId);
        return tag;
    }

    public void fromTag(CompoundTag tag) {
        if (tag.contains("logic", Tag.TAG_COMPOUND)) {
            logic.readFromNBT(tag.getCompound("logic"));
        }
        for (int i = 0; i < PATTERN_SLOTS; i++) slots[i] = null;
        ListTag plans = tag.getList("plans", Tag.TAG_COMPOUND);
        for (int i = 0; i < plans.size(); i++) {
            CompoundTag p = plans.getCompound(i);
            int s = p.getInt("s");
            if (s < 0 || s >= PATTERN_SLOTS || !p.hasUUID("u")) continue;
            ItemStack token = ItemStack.of(p.getCompound("tok"));
            if (token.isEmpty()) continue;
            PlanEntry e = new PlanEntry(p.getUUID("u"), p.getBoolean("a"), token);
            if (p.contains("link", Tag.TAG_COMPOUND) && requester != null) {
                e.link = StorageHelper.loadCraftingLink(p.getCompound("link"), requester);
            }
            slots[s] = e;
        }
        nextPlanId = tag.getLong("nextId");
    }

    public void onLoad() {
        logic.updatePatterns();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private void cancelEntry(PlanEntry e) {
        if (e.link != null) {
            try {
                e.link.cancel();
            } catch (Throwable t) {
                MMMod.LOGGER.debug("uplink: error cancelling auto-craft link", t);
            }
            e.link = null;
        }
        if (e.calc != null) {
            e.calc.cancel(true);
            e.calc = null;
        }
    }

    private void removeSlot(int slot) {
        logic.getPatternInv().setItemDirect(slot, ItemStack.EMPTY);
        slots[slot] = null;
        logic.updatePatterns();
    }

    private int findSlotByToken(AEKey outKey) {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            if (slots[i] != null && outKey.equals(slots[i].tokenKey())) return i;
        }
        return -1;
    }

    private int firstEmptySlot() {
        for (int i = 0; i < PATTERN_SLOTS; i++) {
            if (logic.getPatternInv().getStackInSlot(i).isEmpty()) return i;
        }
        return -1;
    }

    private void notifyOwner(UUID owner, Level level, String key) {
        if (level instanceof ServerLevel sl) {
            ServerPlayer player = sl.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key));
            }
        }
    }

    private IActionSource src() {
        return IActionSource.ofMachine(actionHost);
    }

    @Nullable
    private IGrid grid() {
        return mainNode.getGrid();
    }
}
