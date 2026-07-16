package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.SlotType;

import net.minecraft.core.BlockPos;

import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

public class Shapes {
    /** All positions in the AABB from a to b, classified as CORNER/EDGE/FACE/VOLUME. */
    public static List<ShapeBlock> cube(BlockPos a, BlockPos b) {
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

    /** Bresenham 3D line from a to b (inclusive). All positions are EDGE slots. */
    public static List<ShapeBlock> line(BlockPos a, BlockPos b) {
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

    public static List<ShapeBlock> sphere(BlockPos center, BlockPos radiusPoint) {
        var result = new ArrayList<ShapeBlock>();

        int sx = Math.abs(radiusPoint.getX() - center.getX()) * 2 + 1;
        int sy = Math.abs(radiusPoint.getY() - center.getY()) * 2 + 1;
        int sz = Math.abs(radiusPoint.getZ() - center.getZ()) * 2 + 1;

        int minX = center.getX() - Math.abs(radiusPoint.getX() - center.getX());
        int minY = center.getY() - Math.abs(radiusPoint.getY() - center.getY());
        int minZ = center.getZ() - Math.abs(radiusPoint.getZ() - center.getZ());

        double rx = sx / 2.0, ry = sy / 2.0, rz = sz / 2.0;
        boolean[][][] present = new boolean[sx + 2][sy + 2][sz + 2];

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    double dx = (rx > 1 ? Math.pow((x - rx + 0.5) / rx, 2.0) : 0);
                    double dy = (ry > 1 ? Math.pow((y - ry + 0.5) / ry, 2.0) : 0);
                    double dz = (rz > 1 ? Math.pow((z - rz + 0.5) / rz, 2.0) : 0);
                    if (Math.sqrt(dx + dy + dz) <= 1) present[x + 1][y + 1][z + 1] = true;
                }
            }
        }

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    if (!present[x + 1][y + 1][z + 1]) continue;
                    SlotType slot = isSurface(present, x + 1, y + 1, z + 1) ? SlotType.FACE : SlotType.VOLUME;
                    result.add(new ShapeBlock(new BlockPos(x + minX, y + minY, z + minZ), slot));
                }
            }
        }

        return result;
    }

    public static List<ShapeBlock> cylinder(BlockPos a, BlockPos b, BlockPos c) {
        var result = new ArrayList<ShapeBlock>();

        Vector3i va = new Vector3i(a.getX(), a.getY(), a.getZ());
        Vector3i vb = new Vector3i(b.getX(), b.getY(), b.getZ());
        Vector3i vc = new Vector3i(c.getX(), c.getY(), c.getZ());

        Vector3i b2 = MMConfig.pinToPlanes(va, vb);
        Vector3i height = MMConfig.pinToLine(va, b2, vc).sub(va);
        Vector3i delta = new Vector3i(b2).sub(va);

        delta.x += (int) Math.signum(delta.x);
        delta.y += (int) Math.signum(delta.y);
        delta.z += (int) Math.signum(delta.z);

        int dA = 0, dB = 0, dH = 0;
        Vector3i vecA, vecB, vecH;

        int minComp = -1, minVal = Integer.MAX_VALUE;
        if (Math.abs(delta.x) < minVal) {
            minVal = Math.abs(delta.x);
            minComp = 0;
        }
        if (Math.abs(delta.y) < minVal) {
            minVal = Math.abs(delta.y);
            minComp = 1;
        }
        if (Math.abs(delta.z) < minVal) {
            minVal = Math.abs(delta.z);
            minComp = 2;
        }

        switch (minComp) {
            case 0 -> {
                dA = delta.y;
                dB = delta.z;
                dH = height.x;
                vecA = new Vector3i(0, (int) Math.signum(delta.y), 0);
                vecB = new Vector3i(0, 0, (int) Math.signum(delta.z));
                vecH = new Vector3i((int) Math.signum(height.x), 0, 0);
            }
            case 1 -> {
                dA = delta.x;
                dB = delta.z;
                dH = height.y;
                vecA = new Vector3i((int) Math.signum(delta.x), 0, 0);
                vecB = new Vector3i(0, 0, (int) Math.signum(delta.z));
                vecH = new Vector3i(0, (int) Math.signum(height.y), 0);
            }
            case 2 -> {
                dA = delta.x;
                dB = delta.y;
                dH = height.z;
                vecA = new Vector3i((int) Math.signum(delta.x), 0, 0);
                vecB = new Vector3i(0, (int) Math.signum(delta.y), 0);
                vecH = new Vector3i(0, 0, (int) Math.signum(height.z));
            }
            default -> throw new IllegalStateException();
        }

        int absA = Math.abs(dA), absB = Math.abs(dB), absH = Math.abs(dH) + 1;
        float rA = absA / 2f, rB = absB / 2f;
        boolean[][][] present = new boolean[absA + 2][absH + 2][absB + 2];

        for (int aIdx = 0; aIdx < absA; aIdx++) {
            for (int bIdx = 0; bIdx < absB; bIdx++) {
                double distance = Math.pow((aIdx - rA + 0.5) / rA, 2.0) + Math.pow((bIdx - rB + 0.5) / rB, 2.0);
                if (distance <= 1.0) {
                    for (int hIdx = 0; hIdx < absH; hIdx++) present[aIdx + 1][hIdx + 1][bIdx + 1] = true;
                }
            }
        }

        for (int aIdx = 0; aIdx < absA; aIdx++) {
            for (int hIdx = 0; hIdx < absH; hIdx++) {
                for (int bIdx = 0; bIdx < absB; bIdx++) {
                    if (!present[aIdx + 1][hIdx + 1][bIdx + 1]) continue;
                    SlotType slot = isSurface(present, aIdx + 1, hIdx + 1, bIdx + 1) ? SlotType.FACE : SlotType.VOLUME;
                    BlockPos pos = new BlockPos(
                            aIdx * vecA.x + bIdx * vecB.x + hIdx * vecH.x + a.getX(),
                            aIdx * vecA.y + bIdx * vecB.y + hIdx * vecH.y + a.getY(),
                            aIdx * vecA.z + bIdx * vecB.z + hIdx * vecH.z + a.getZ());
                    result.add(new ShapeBlock(pos, slot));
                }
            }
        }

        return result;
    }

    private static boolean isSurface(boolean[][][] grid, int x, int y, int z) {
        return !grid[x - 1][y][z] || !grid[x + 1][y][z] || !grid[x][y - 1][z] || !grid[x][y + 1][z] || !grid[x][y][z - 1] || !grid[x][y][z + 1];
    }
}
