package dev.wp.matter_manipulator.common.building;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class BlockAnalyzer {

    /**
     * Reads all non-air blocks in the AABB from cornerA to cornerB (inclusive).
     * Returned blocks have positions relative to cornerA.
     */
    public static List<PendingBlock> analyzeRegion(Level level, BlockPos cornerA, BlockPos cornerB) {
        var result = new ArrayList<PendingBlock>();

        int minX = Math.min(cornerA.getX(), cornerB.getX());
        int minY = Math.min(cornerA.getY(), cornerB.getY());
        int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        int maxX = Math.max(cornerA.getX(), cornerB.getX());
        int maxY = Math.max(cornerA.getY(), cornerB.getY());
        int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var worldPos = new BlockPos(x, y, z);
                    var spec = BlockSpec.fromWorld(level, worldPos);
                    if (spec.skipWhenCopying()) continue;
                    var relPos = new BlockPos(x - cornerA.getX(), y - cornerA.getY(), z - cornerA.getZ());
                    result.add(new PendingBlock(relPos, spec));
                }
            }
        }

        // Sort: solid first, then attachments
        result.sort((a, b) -> Integer.compare(a.buildOrder, b.buildOrder));
        return result;
    }
}
