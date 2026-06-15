package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class PendingBlock {
    /** Position relative to paste anchor. */
    public final BlockPos relPos;

    public final BlockSpec spec;
    /** Lower = placed first (solid blocks before attachments). */
    public final int buildOrder;

    /**
     * Captured tile state to reproduce after the block is placed (covers, AE parts, stored items,
     * fluids). Empty for fresh placement (geometry/exchange/cables) — that is the whole point of the
     * split: a block carries analyses only when a copy captured them. See {@link ITileAnalysis}.
     */
    public final List<ITileAnalysis> analyses = new ArrayList<>();

    public PendingBlock(BlockPos relPos, BlockSpec spec) {
        this(relPos, spec, computeOrder(spec));
    }

    public PendingBlock(BlockPos relPos, BlockSpec spec, int buildOrder) {
        this.relPos = relPos;
        this.spec = spec;
        this.buildOrder = buildOrder;
    }

    /** A copy at a new relative position, deep-copying analyses so transforms/applies don't alias. */
    public PendingBlock at(BlockPos newRelPos) {
        PendingBlock copy = new PendingBlock(newRelPos, spec, buildOrder);
        for (ITileAnalysis a : analyses) copy.analyses.add(a.copy());
        return copy;
    }

    public void addAnalysis(ITileAnalysis analysis) {
        if (analysis != null) analyses.add(analysis);
    }

    /** Rotate/flip every attached analysis to follow a build transform. */
    public void transform(MMTransform transform) {
        for (ITileAnalysis a : analyses) a.transform(transform);
    }

    /** Reproduce all captured tile state on the freshly placed block; returns false on any failure. */
    public boolean apply(BlockApplyContext ctx) {
        boolean ok = true;
        for (ITileAnalysis a : analyses) ok &= a.apply(ctx);
        return ok;
    }

    /** Tally the items/fluids the attached analyses would install. */
    public void collectRequiredItems(RequiredItems out) {
        for (ITileAnalysis a : analyses) a.collectRequiredItems(out);
    }

    private static int computeOrder(BlockSpec spec) {
        // Blocks that need a solid base go after solid blocks
        if (spec.state.canBeReplaced()) return 0;
        if (!spec.state.isSolid()) return 10;
        return 5;
    }
}
