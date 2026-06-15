package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.nbt.CompoundTag;

/**
 * A parsed slice of a captured block's tile state — covers, AE parts, stored items, fluids — that
 * knows how to reproduce itself on a freshly placed block. This mirrors the original mod's
 * {@code ITileAnalysisIntegration}: instead of carrying a block entity's raw NBT around in the
 * {@link dev.wp.matter_manipulator.common.building.BlockSpec}, each captured concern is its own
 * object, attached to a {@link dev.wp.matter_manipulator.common.building.PendingBlock} only when a
 * copy actually captured it. Fresh placement (geometry/exchange/cables) simply attaches none.
 */
public interface ITileAnalysis {
    /**
     * Reproduce this captured state on the block entity at {@code ctx}'s position, charging the
     * player for anything it installs. Returns false if it could not be fully applied (so the build
     * can surface a failure). Runs server-side after the block has been placed.
     */
    boolean apply(BlockApplyContext ctx);

    /** Tally the items/fluids this analysis would install, for the required-items preview. */
    void collectRequiredItems(RequiredItems out);

    /** Rotate/flip any orientation this analysis encodes (cover sides, part sides, facing). */
    void transform(MMTransform transform);

    /** A deep, independent copy (analyses are mutated by {@link #transform}). */
    ITileAnalysis copy();

    /**
     * Contributes block-entity NBT for the ghost preview to reconstruct a throwaway BE and pull its
     * model data (e.g. GT covers, AE parts that render from BE state). Default no-op; analyses that
     * only affect items/fluids (not the model) leave it alone.
     */
    default void contributePreviewNbt(CompoundTag out) {}
}
