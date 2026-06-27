package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

/**
 * Central registry of per-{@link BlockEntity} soft-dep {@link CompatHandler}s. Handlers are registered
 * once in {@code MMMod.commonSetup}, each behind its own {@code Mods.X} gate (AE2 before GregTech, to
 * preserve the original AE-first precedence). Core build/analysis code dispatches through the combinators
 * here instead of branching on mod id, and never imports the soft-dep classes directly.
 */
public final class CompatRegistry {
    private static final List<CompatHandler> HANDLERS = new CopyOnWriteArrayList<>();

    private CompatRegistry() {}

    /** Registers a handler, ignoring a second handler of the same class so {@code commonSetup} stays idempotent. */
    public static void register(CompatHandler handler) {
        if (handler == null) return;
        for (CompatHandler existing : HANDLERS) {
            if (existing.getClass() == handler.getClass()) return;
        }
        HANDLERS.add(handler);
    }

    /** A captured copy analysis plus whether its handler fully reproduces the BE. */
    public record TileMatch(ITileAnalysis analysis, boolean exclusive) {}

    /** First handler that owns {@code be}, or {@code null} if none. */
    @Nullable
    public static TileMatch analyze(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            ITileAnalysis a = h.analyze(be);
            if (a != null) return new TileMatch(a, h.isExclusive());
        }
        return null;
    }

    /** Cables-mode capture: the first handler's cable NBT, or {@code null}. */
    @Nullable
    public static CompoundTag analyzeCable(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            CompoundTag nbt = h.analyzeCable(be);
            if (nbt != null) return nbt;
        }
        return null;
    }

    /** Rebuild a persisted cables-mode analysis, or {@code null} if no handler claims the block id. */
    @Nullable
    public static ITileAnalysis reconstructCable(String blockId, CompoundTag nbt) {
        for (CompatHandler h : HANDLERS) {
            ITileAnalysis a = h.reconstructCable(blockId, nbt);
            if (a != null) return a;
        }
        return null;
    }

    /** MOVE: let every handler rotate its own orientation in the verbatim NBT (mutually exclusive tag types). */
    public static void transformRawNbt(CompoundTag data, MMTransform transform) {
        for (CompatHandler h : HANDLERS) h.transformRawNbt(data, transform);
    }

    /** MOVE: first handler to take over the raw load; false if none (caller loads normally). */
    public static boolean restoreRaw(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        for (CompatHandler h : HANDLERS) {
            if (h.restoreRaw(level, pos, state, tag)) return true;
        }
        return false;
    }

    /** MOVE: post-load fixups across all handlers. */
    public static void afterRawLoad(ServerLevel level, BlockPos pos, BlockEntity be) {
        for (CompatHandler h : HANDLERS) h.afterRawLoad(level, pos, be);
    }

    /** Paste: post-apply fixups across all handlers. */
    public static void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be) {
        for (CompatHandler h : HANDLERS) h.afterPaste(level, pos, be);
    }

    /** Overwrite: first handler to fully disassemble {@code be}; false if none. */
    public static boolean fullyDismantle(BlockEntity be, MMInventory inv) {
        for (CompatHandler h : HANDLERS) {
            if (h.fullyDismantle(be, inv)) return true;
        }
        return false;
    }

    /** Overwrite: let every handler salvage its attachments before the generic drops. */
    public static void salvageBeforeDrops(BlockEntity be, MMInventory inv) {
        for (CompatHandler h : HANDLERS) h.salvageBeforeDrops(be, inv);
    }

    /** Overwrite: true if any handler reports this BE drops via {@code Block.getDrops}. */
    public static boolean usesNativeDrops(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            if (h.usesNativeDrops(be)) return true;
        }
        return false;
    }

    /** Overwrite: true if any handler says this BE's item contents must not be reclaimed. */
    public static boolean skipContentReclaim(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            if (h.skipContentReclaim(be)) return true;
        }
        return false;
    }

    /** Overwrite: true if any handler says this BE's tanks must not be drained. */
    public static boolean skipTankDrain(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            if (h.skipTankDrain(be)) return true;
        }
        return false;
    }

    /** Copy capture: true if any handler says this BE's tanks front the network (skip fluid capture). */
    public static boolean skipFluidCapture(BlockEntity be) {
        for (CompatHandler h : HANDLERS) {
            if (h.skipFluidCapture(be)) return true;
        }
        return false;
    }

    /** Copy capture: combined item-handler slots to skip (union across handlers); never {@code null}. */
    public static IntPredicate excludedInventorySlots(BlockEntity be) {
        IntPredicate p = null;
        for (CompatHandler h : HANDLERS) {
            IntPredicate hp = h.excludedInventorySlots(be);
            if (hp != null) p = (p == null) ? hp : p.or(hp);
        }
        return p == null ? (i -> false) : p;
    }
}
