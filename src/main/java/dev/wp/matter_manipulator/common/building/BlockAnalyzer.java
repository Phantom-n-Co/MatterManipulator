package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class BlockAnalyzer {
    public static List<PendingBlock> analyzeRegion(Level level, BlockPos cornerA, BlockPos cornerB, boolean moving) {
        return analyzeRegion(level, cornerA, cornerB, moving, null);
    }

    /**
     * Reads all non-air blocks in the AABB from cornerA to cornerB (inclusive).
     * Returned blocks have positions relative to cornerA.
     *
     * <p>For a MOVING build the source is broken and relocated, so when a {@code builder} is supplied
     * (server build only) a protected or invulnerable source block is skipped at capture: it gets no
     * destination block and {@code PendingBuild} likewise leaves it in place. The client preview and
     * required-items pass {@code null} and capture everything (a cosmetic-only inaccuracy).
     */
    public static List<PendingBlock> analyzeRegion(
            Level level, BlockPos cornerA, BlockPos cornerB, boolean moving, @Nullable Player builder) {
        var result = new ArrayList<PendingBlock>();

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        boolean checkProtection = moving && builder != null && level instanceof ServerLevel;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var worldPos = new BlockPos(x, y, z);
                    var spec = BlockSpec.fromWorld(level, worldPos);
                    if (spec.skipWhenCopying()) continue;
                    if (checkProtection) {
                        var sl = (ServerLevel) level;
                        if (spec.state.getDestroySpeed(sl, worldPos) < 0
                                || !BuildProtection.canBreak(sl, worldPos, builder)) {
                            continue; // protected/invulnerable source: don't relocate it
                        }
                    }
                    var relPos = new BlockPos(x - cornerA.getX(), y - cornerA.getY(), z - cornerA.getZ());
                    var pb = new PendingBlock(relPos, spec);
                    pb.analyze(level, worldPos, moving);
                    result.add(pb);
                }
            }
        }

        // Sort: solid first, then attachments
        result.sort((a, b) -> Integer.compare(a.buildOrder, b.buildOrder));
        return result;
    }
}
