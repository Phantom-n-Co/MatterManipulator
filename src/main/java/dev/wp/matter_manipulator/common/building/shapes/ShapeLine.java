package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.SlotType;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ShapeLine {
    /** Bresenham 3D line from a to b (inclusive). All positions are EDGE slots. */
    public static List<ShapeBlock> generate(BlockPos a, BlockPos b) {
        var result = new ArrayList<ShapeBlock>();

        int x0 = a.getX(), y0 = a.getY(), z0 = a.getZ();
        int x1 = b.getX(), y1 = b.getY(), z1 = b.getZ();

        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;

        result.add(new ShapeBlock(new BlockPos(x0, y0, z0), SlotType.EDGE));

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx, p2 = 2 * dz - dx;
            while (x0 != x1) {
                x0 += sx;
                if (p1 >= 0) {
                    y0 += sy;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z0 += sz;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                result.add(new ShapeBlock(new BlockPos(x0, y0, z0), SlotType.EDGE));
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy, p2 = 2 * dz - dy;
            while (y0 != y1) {
                y0 += sy;
                if (p1 >= 0) {
                    x0 += sx;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z0 += sz;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                result.add(new ShapeBlock(new BlockPos(x0, y0, z0), SlotType.EDGE));
            }
        } else {
            int p1 = 2 * dy - dz, p2 = 2 * dx - dz;
            while (z0 != z1) {
                z0 += sz;
                if (p1 >= 0) {
                    y0 += sy;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x0 += sx;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
                result.add(new ShapeBlock(new BlockPos(x0, y0, z0), SlotType.EDGE));
            }
        }

        return result;
    }
}
