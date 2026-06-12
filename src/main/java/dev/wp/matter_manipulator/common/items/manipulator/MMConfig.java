package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.joml.Vector3i;

import java.util.Arrays;
import java.util.Objects;

public class MMConfig {

    public PlaceMode placeMode = PlaceMode.GEOMETRY;
    public RemoveMode removeMode = RemoveMode.NONE;
    public ShapeType shape = ShapeType.CUBE;

    public Location coordA = null;
    public Location coordB = null;
    public Location coordC = null;

    public Vector3i coordAOffset = null;
    public Vector3i coordBOffset = null;
    public Vector3i coordCOffset = null;

    public WeightedSpecList corners = new WeightedSpecList();
    public WeightedSpecList edges = new WeightedSpecList();
    public WeightedSpecList faces = new WeightedSpecList();
    public WeightedSpecList volumes = new WeightedSpecList();

    public WeightedSpecList replaceWhitelist = new WeightedSpecList();
    public WeightedSpecList replaceWith = new WeightedSpecList();
    public BlockSpec cables = BlockSpec.air();

    public int[] arraySpan = new int[]{0, 0, 0};
    public MMTransform transform = new MMTransform();

    /**
     * Captured blocks for COPYING/MOVING modes, stored as a relative-position NBT list.
     */
    public CompoundTag capturedRegion = null;
    /**
     * Origin of the captured region (coordA at time of capture).
     */
    public Location captureOrigin = null;

    /**
     * Pending one-shot action that the next right-click block will fulfil (server-side).
     */
    public PendingAction action = null;
    /**
     * Which slot type is being targeted when the blockSelectMode action is active.
     */
    public BlockSelectMode blockSelectMode = BlockSelectMode.ALL;

    public boolean locked = false;

    public MMConfig() {
    }

    public MMConfig copy() {
        MMConfig next = new MMConfig();
        next.placeMode = this.placeMode;
        next.removeMode = this.removeMode;
        next.shape = this.shape;
        next.coordA = this.coordA;
        next.coordB = this.coordB;
        next.coordC = this.coordC;
        next.coordAOffset = this.coordAOffset != null ? new Vector3i(this.coordAOffset) : null;
        next.coordBOffset = this.coordBOffset != null ? new Vector3i(this.coordBOffset) : null;
        next.coordCOffset = this.coordCOffset != null ? new Vector3i(this.coordCOffset) : null;
        next.corners = this.corners;
        next.edges = this.edges;
        next.faces = this.faces;
        next.volumes = this.volumes;
        next.replaceWhitelist = this.replaceWhitelist;
        next.replaceWith = this.replaceWith;
        next.cables = this.cables;
        next.arraySpan = this.arraySpan.clone();
        next.transform = this.transform.copy();
        next.capturedRegion = this.capturedRegion != null ? this.capturedRegion.copy() : null;
        next.captureOrigin = this.captureOrigin;
        next.action = this.action;
        next.blockSelectMode = this.blockSelectMode;
        next.locked = this.locked;
        return next;
    }

    public void clearCoords() {
        coordA = null;
        coordB = null;
        coordC = null;
        coordAOffset = null;
        coordBOffset = null;
        coordCOffset = null;
        capturedRegion = null;
        captureOrigin = null;
    }

    public BlockPos getCoordA(Level level, BlockPos lookingAt) {
        if (coordAOffset == null) return coordA != null ? coordA.pos : null;
        return lookingAt.offset(coordAOffset.x, coordAOffset.y, coordAOffset.z);
    }

    public BlockPos getCoordB(Level level, BlockPos lookingAt) {
        if (coordBOffset == null) return coordB != null ? coordB.pos : null;
        return lookingAt.offset(coordBOffset.x, coordBOffset.y, coordBOffset.z);
    }

    public BlockPos getCoordC(Level level, BlockPos lookingAt) {
        if (coordCOffset == null) return coordC != null ? coordC.pos : null;
        return lookingAt.offset(coordCOffset.x, coordCOffset.y, coordCOffset.z);
    }

    public static Vector3i pinToPlanes(Vector3i origin, Vector3i point) {
        int dX = Math.abs(point.x - origin.x);
        int dY = Math.abs(point.y - origin.y);
        int dZ = Math.abs(point.z - origin.z);

        int shortest = Math.min(dX, Math.min(dY, dZ));

        if (shortest == dX) {
            return new Vector3i(origin.x, point.y, point.z);
        } else if (shortest == dY) {
            return new Vector3i(point.x, origin.y, point.z);
        } else {
            return new Vector3i(point.x, point.y, origin.z);
        }
    }

    public static Vector3i pinToLine(Vector3i origin, Vector3i b, Vector3i point) {
        Vector3i delta = new Vector3i(b).sub(origin);
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

        return switch (minComp) {
            case 0 -> new Vector3i(point.x, origin.y, origin.z);
            case 1 -> new Vector3i(origin.x, point.y, origin.z);
            case 2 -> new Vector3i(origin.x, origin.y, point.z);
            default -> throw new AssertionError();
        };
    }

    public static Vector3i pinToAxes(Vector3i origin, Vector3i point) {
        Vector3i delta = new Vector3i(point).sub(origin);
        int maxComp = -1;
        int maxVal = Integer.MIN_VALUE;
        if (Math.abs(delta.x) > maxVal) {
            maxVal = Math.abs(delta.x);
            maxComp = 0;
        }
        if (Math.abs(delta.y) > maxVal) {
            maxVal = Math.abs(delta.y);
            maxComp = 1;
        }
        if (Math.abs(delta.z) > maxVal) {
            maxVal = Math.abs(delta.z);
            maxComp = 2;
        }

        return switch (maxComp) {
            case 0 -> new Vector3i(point.x, origin.y, origin.z);
            case 1 -> new Vector3i(origin.x, point.y, origin.z);
            case 2 -> new Vector3i(origin.x, origin.y, point.z);
            default -> throw new AssertionError();
        };
    }

    /**
     * Computes the array span from the paste origin (coordC) to {@code lookingAt}:
     * how many extra copies of the region fit per axis. Ported from the 1.7.10 getArrayMult.
     * Requires coordA/B/C to be set.
     */
    public Vector3i getArrayMult(BlockPos lookingAt) {
        Vector3i array = new Vector3i(
                lookingAt.getX() - coordC.pos.getX(),
                lookingAt.getY() - coordC.pos.getY(),
                lookingAt.getZ() - coordC.pos.getZ());

        Vector3i delta = new Vector3i(
                coordB.pos.getX() - coordA.pos.getX(),
                coordB.pos.getY() - coordA.pos.getY(),
                coordB.pos.getZ() - coordA.pos.getZ());

        if (transform != null) {
            array = transform.applyInverse(array);
        }

        array.x = delta.x == 0 ? array.x : Math.floorDiv(array.x, delta.x + (delta.x < 0 ? -1 : 1));
        array.y = delta.y == 0 ? array.y : Math.floorDiv(array.y, delta.y + (delta.y < 0 ? -1 : 1));
        array.z = delta.z == 0 ? array.z : Math.floorDiv(array.z, delta.z + (delta.z < 0 ? -1 : 1));

        return array;
    }

    public boolean hasRegion() {
        return coordA != null && coordB != null;
    }

    public boolean hasPasteTarget() {
        return coordC != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MMConfig mmConfig = (MMConfig) o;
        return locked == mmConfig.locked && placeMode == mmConfig.placeMode && removeMode == mmConfig.removeMode && shape == mmConfig.shape && Objects.equals(coordA, mmConfig.coordA) && Objects.equals(coordB, mmConfig.coordB) && Objects.equals(coordC, mmConfig.coordC) && Objects.equals(coordAOffset, mmConfig.coordAOffset) && Objects.equals(coordBOffset, mmConfig.coordBOffset) && Objects.equals(coordCOffset, mmConfig.coordCOffset) && Arrays.equals(arraySpan, mmConfig.arraySpan) && Objects.equals(transform, mmConfig.transform) && action == mmConfig.action && blockSelectMode == mmConfig.blockSelectMode;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(placeMode, removeMode, shape, coordA, coordB, coordC, coordAOffset, coordBOffset, coordCOffset, transform, action, blockSelectMode, locked);
        result = 31 * result + Arrays.hashCode(arraySpan);
        return result;
    }

    public void rotate(Direction axis, int amount) {
        transform.rotate(axis, amount);
    }
}
