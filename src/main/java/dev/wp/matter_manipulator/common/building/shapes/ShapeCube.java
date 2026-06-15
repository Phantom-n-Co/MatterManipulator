package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.SlotType;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ShapeCube {
    /**
     * Iterates all positions in the AABB from a to b and classifies each as
     * CORNER, EDGE, FACE, or VOLUME.
     */
    public static List<ShapeBlock> generate(BlockPos a, BlockPos b) {
        var result = new ArrayList<ShapeBlock>();

        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean onX = (x == minX || x == maxX);
                    boolean onY = (y == minY || y == maxY);
                    boolean onZ = (z == minZ || z == maxZ);

                    int faceCount = (onX ? 1 : 0) + (onY ? 1 : 0) + (onZ ? 1 : 0);
                    SlotType slot = switch (faceCount) {
                        case 3 -> SlotType.CORNER;
                        case 2 -> SlotType.EDGE;
                        case 1 -> SlotType.FACE;
                        default -> SlotType.VOLUME;
                    };
                    result.add(new ShapeBlock(new BlockPos(x, y, z), slot));
                }
            }
        }

        return result;
    }
}
