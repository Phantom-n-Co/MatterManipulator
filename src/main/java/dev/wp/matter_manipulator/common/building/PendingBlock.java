package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.building.analysis.BlockApplyContext;
import dev.wp.matter_manipulator.common.building.analysis.FluidAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.InventoryAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RawTileAnalysis;
import dev.wp.matter_manipulator.common.building.analysis.RequiredItems;
import dev.wp.matter_manipulator.common.building.analysis.VanillaMiscAnalysis;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

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

    /**
     * Captures the tile state at {@code worldPos} into analyses (called during a copy/move). MOVING
     * keeps everything verbatim ({@link RawTileAnalysis}); COPYING attaches per-concern analyses:
     * AE2 reproduces its own parts/contents, so it stands alone; otherwise a GT machine's config (or
     * a vanilla block's misc config) followed by the generic item-handler contents and tank fluids.
     * Order matters — config (which may recreate the BE) runs before the inventory fill.
     */
    public void analyze(Level level, BlockPos worldPos, boolean moving) {
        BlockEntity be = level.getBlockEntity(worldPos);
        if (be == null) return;
        if (moving) {
            addAnalysis(RawTileAnalysis.of(be.saveWithFullMetadata()));
            return;
        }
        if (Mods.AE2 && AECompat.isAEBlock(be)) {
            addAnalysis(AECompat.AEAnalysis.analyze(be));
            return; // AE re-initialises its own grid and reproduces parts/contents wholesale
        }
        boolean gt = false;
        if (Mods.GTCEU) {
            ITileAnalysis gtAnalysis = GTCompat.GTMachineAnalysis.analyze(be);
            if (gtAnalysis != null) {
                addAnalysis(gtAnalysis);
                gt = true;
            }
        }
        if (!gt) addAnalysis(VanillaMiscAnalysis.analyze(be));
        addAnalysis(InventoryAnalysis.analyze(be));
        addAnalysis(FluidAnalysis.analyze(be));
    }

    /** Block-entity NBT for the ghost preview to reconstruct model data, or null if none is needed. */
    public CompoundTag previewTileData() {
        CompoundTag out = new CompoundTag();
        for (ITileAnalysis a : analyses) a.contributePreviewNbt(out);
        return out.isEmpty() ? null : out;
    }

    /** Rotate/flip every attached analysis to follow a build transform. */
    public void transform(MMTransform transform) {
        for (ITileAnalysis a : analyses) a.transform(transform);
    }

    /** Reproduce all captured tile state on the freshly placed block; returns false on any failure. */
    public boolean apply(BlockApplyContext ctx) {
        boolean ok = true;
        for (ITileAnalysis a : analyses) ok &= a.apply(ctx);
        // The block was placed (and neighbours notified) before the analyses ran, so re-notify now that
        // the BE carries its restored state — mirrors the original PendingBlock.apply's post-apply
        // notifyBlockOfNeighborChange + AE2 part-cache reset, letting pasted parts/machines integrate
        // (e.g. a storage bus scanning its adjacent inventory) without waiting for the next block update.
        var level = ctx.level();
        BlockPos pos = ctx.pos();
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        if (Mods.AE2) AECompat.markHostForUpdate(level, pos);
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
