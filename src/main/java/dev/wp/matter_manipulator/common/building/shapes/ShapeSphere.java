package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.SlotType;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ShapeSphere {
    public static List<ShapeBlock> generate(BlockPos center, BlockPos radiusPoint) {
        var result = new ArrayList<ShapeBlock>();

        int sx = Math.abs(radiusPoint.getX() - center.getX()) * 2 + 1;
        int sy = Math.abs(radiusPoint.getY() - center.getY()) * 2 + 1;
        int sz = Math.abs(radiusPoint.getZ() - center.getZ()) * 2 + 1;

        int minX = center.getX() - Math.abs(radiusPoint.getX() - center.getX());
        int minY = center.getY() - Math.abs(radiusPoint.getY() - center.getY());
        int minZ = center.getZ() - Math.abs(radiusPoint.getZ() - center.getZ());

        double rx = sx / 2.0;
        double ry = sy / 2.0;
        double rz = sz / 2.0;

        boolean[][][] present = new boolean[sx + 2][sy + 2][sz + 2];

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    double dx = (rx > 1 ? Math.pow((x - rx + 0.5) / rx, 2.0) : 0);
                    double dy = (ry > 1 ? Math.pow((y - ry + 0.5) / ry, 2.0) : 0);
                    double dz = (rz > 1 ? Math.pow((z - rz + 0.5) / rz, 2.0) : 0);
                    double distance = Math.sqrt(dx + dy + dz);

                    if (distance <= 1) {
                        present[x + 1][y + 1][z + 1] = true;
                    }
                }
            }
        }

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    if (!present[x + 1][y + 1][z + 1]) continue;

                    boolean onSurface = false;
                    if (rx > 1 && (!present[x][y + 1][z + 1] || !present[x + 2][y + 1][z + 1])) onSurface = true;
                    if (!onSurface && ry > 1 && (!present[x + 1][y][z + 1] || !present[x + 1][y + 2][z + 1]))
                        onSurface = true;
                    if (!onSurface && rz > 1 && (!present[x + 1][y + 1][z] || !present[x + 1][y + 1][z + 2]))
                        onSurface = true;

                    SlotType slot = onSurface ? SlotType.FACE : SlotType.VOLUME;
                    result.add(new ShapeBlock(new BlockPos(x + minX, y + minY, z + minZ), slot));
                }
            }
        }

        return result;
    }
}
