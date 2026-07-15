package dev.wp.matter_manipulator.common.building.shapes;

import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.SlotType;

import net.minecraft.core.BlockPos;

import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

public class ShapeCylinder {
    public static List<ShapeBlock> generate(BlockPos a, BlockPos b, BlockPos c) {
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

        int minComp = -1;
        int minVal = Integer.MAX_VALUE;
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
                    for (int hIdx = 0; hIdx < absH; hIdx++) {
                        present[aIdx + 1][hIdx + 1][bIdx + 1] = true;
                    }
                }
            }
        }

        for (int aIdx = 0; aIdx < absA; aIdx++) {
            for (int hIdx = 0; hIdx < absH; hIdx++) {
                for (int bIdx = 0; bIdx < absB; bIdx++) {
                    if (!present[aIdx + 1][hIdx + 1][bIdx + 1]) continue;

                    boolean onSurface = ShapeSphere.isSurface(present, aIdx + 1, hIdx + 1, bIdx + 1);
                    SlotType slot = onSurface ? SlotType.FACE : SlotType.VOLUME;

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
}
