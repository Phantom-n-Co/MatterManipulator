package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;

public class PendingBlock {

    /** Position relative to paste anchor. */
    public final BlockPos relPos;

    public final BlockSpec spec;
    /** Lower = placed first (solid blocks before attachments). */
    public final int buildOrder;

    public PendingBlock(BlockPos relPos, BlockSpec spec) {
        this(relPos, spec, computeOrder(spec));
    }

    public PendingBlock(BlockPos relPos, BlockSpec spec, int buildOrder) {
        this.relPos = relPos;
        this.spec = spec;
        this.buildOrder = buildOrder;
    }

    private static int computeOrder(BlockSpec spec) {
        var block = spec.state.getBlock();
        // Blocks that need a solid base go after solid blocks
        if (spec.state.canBeReplaced()) return 0;
        if (!spec.state.isSolid()) return 10;
        return 5;
    }
}
