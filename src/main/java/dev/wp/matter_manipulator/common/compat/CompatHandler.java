package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Per-{@link BlockEntity} soft-dependency tile compat, registered once per mod in
 * {@link CompatRegistry}. Each handler is a thin adapter over a static compat class (e.g.
 * {@code AECompat}/{@code GTCompat}); the core build/analysis code only ever talks to
 * {@link CompatRegistry}, so it carries no AE2/GregTech imports. This interface likewise references
 * only vanilla and MM-core types — keeping the soft-dep classes off the classpath until a handler is
 * actually registered (which only happens behind a {@code Mods.X} gate).
 *
 * <p>Every method except {@link #analyze} is a {@code default} no-op/{@code false}/{@code null}, so a
 * handler implements only the hooks its mod needs.
 */
public interface CompatHandler {
    /** Copy capture: a captured analysis for this BE, or {@code null} if this handler doesn't own it. */
    @Nullable
    ITileAnalysis analyze(BlockEntity be);

    /** True if the analysis fully reproduces the BE, so the generic inventory/fluid fill must be skipped. */
    default boolean isExclusive() {
        return false;
    }

    /** Cables-mode capture: the cable's minimal identity/paint NBT, or {@code null} if not ours. */
    @Nullable
    default CompoundTag analyzeCable(BlockEntity be) {
        return null;
    }

    /** Rebuild a persisted cables-mode analysis from NBT (keyed by block id), or {@code null} if not ours. */
    @Nullable
    default ITileAnalysis reconstructCable(String blockId, CompoundTag nbt) {
        return null;
    }

    /** MOVE: rotate/flip any orientation baked into the verbatim tile NBT. No-ops on tags that aren't ours. */
    default void transformRawNbt(CompoundTag data, MMTransform transform) {}

    /**
     * MOVE: custom raw load (e.g. GT machines must reinit in chunk-load order). Returns true if handled,
     * in which case the caller skips its own {@code be.load}; false to let the caller load normally.
     */
    default boolean restoreRaw(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        return false;
    }

    /** Post-load fixups after a raw (MOVE) load — e.g. GT cover reinit + client sync. */
    default void afterRawLoad(ServerLevel level, BlockPos pos, BlockEntity be) {}

    /** Post-apply fixups after a paste — e.g. AE host markForUpdate so attached parts re-scan. */
    default void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be) {}

    /**
     * Overwrite: hand-disassemble this BE into {@code inv} (parts/cells/etc.). Returns true if fully
     * handled (the caller then drops nothing else); false to fall through to the generic drop path.
     */
    default boolean fullyDismantle(BlockEntity be, MMInventory inv) {
        return false;
    }

    /** Overwrite: salvage attachments that vanilla drops miss (e.g. GT covers) before the generic drops. */
    default void salvageBeforeDrops(BlockEntity be, MMInventory inv) {}

    /** Overwrite: true if this BE drops via {@code Block.getDrops} (its item bakes in contents) vs. a plain item. */
    default boolean usesNativeDrops(BlockEntity be) {
        return false;
    }

    /** Overwrite: true if item contents must NOT be reclaimed (baked into the drop, or network-backed). */
    default boolean skipContentReclaim(BlockEntity be) {
        return false;
    }

    /** Overwrite: true if tanks must NOT be drained (network-backed, e.g. an ME stocking hatch). */
    default boolean skipTankDrain(BlockEntity be) {
        return false;
    }
}
