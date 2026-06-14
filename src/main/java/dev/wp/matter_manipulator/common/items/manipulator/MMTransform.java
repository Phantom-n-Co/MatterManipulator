package dev.wp.matter_manipulator.common.items.manipulator;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.Objects;

/** Represents the rotation and flipping of a build region. */
public class MMTransform {

    public boolean flipX, flipY, flipZ;
    public Direction forward = Direction.NORTH;
    public Direction up = Direction.UP;

    public transient Matrix4f rotation;

    public static final int FLIP_X = 0b1, FLIP_Y = 0b10, FLIP_Z = 0b100;

    public MMTransform() {}

    public Matrix4f getRotation() {
        if (rotation != null) return rotation;

        Matrix4f flip = new Matrix4f();
        flip.scale(flipX ? -1 : 1, flipY ? -1 : 1, flipZ ? -1 : 1);

        // lookAlong in JOML: lookAlong(dir, up)
        Matrix4f rot = new Matrix4f().lookAlong(v(forward), v(up));

        rotation = rot.mul(flip);
        return rotation;
    }

    public void uncache() {
        rotation = null;
    }

    public Vector3i apply(Vector3i v) {
        Vector3f v2 = new Vector3f(v.x(), v.y(), v.z()).mulTransposeDirection(getRotation());
        return new Vector3i(Math.round(v2.x), Math.round(v2.y), Math.round(v2.z));
    }

    /** Rotates a facing direction by this transform, consistent with {@link #apply(Vector3i)}. */
    public Direction apply(Direction dir) {
        return transform(dir, getRotation());
    }

    /** True when this transform does nothing (no rotation, no flip). */
    public boolean isIdentity() {
        return !flipX && !flipY && !flipZ && forward == Direction.NORTH && up == Direction.UP;
    }

    private static final Direction[] HORIZONTALS = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /** A vanilla rotation + mirror equivalent of this transform (for {@code BlockState.rotate/mirror}). */
    public record RotMir(Rotation rotation, Mirror mirror) {}

    /**
     * Finds the {@link Rotation}/{@link Mirror} pair whose effect on the horizontal directions matches
     * this transform, so vanilla block rotation (shape/axis/facing) follows the structure. Returns
     * {@code NONE/NONE} for transforms with a vertical component vanilla can't express (facing is then
     * handled per-property in {@code BuildGenerator}).
     */
    public RotMir deriveRotationMirror() {
        for (Mirror m : Mirror.values()) {
            for (Rotation r : Rotation.values()) {
                boolean ok = true;
                for (Direction d : HORIZONTALS) {
                    if (r.rotate(m.mirror(d)) != apply(d)) {
                        ok = false;
                        break;
                    }
                }
                if (ok) return new RotMir(r, m);
            }
        }
        return new RotMir(Rotation.NONE, Mirror.NONE);
    }

    /** Applies the inverse transform, used to map world-space deltas back into copy space. */
    public Vector3i applyInverse(Vector3i v) {
        Vector3f v2 = new Vector3f(v.x(), v.y(), v.z())
                .mulTransposeDirection(new Matrix4f(getRotation()).invert());
        return new Vector3i(Math.round(v2.x), Math.round(v2.y), Math.round(v2.z));
    }

    public void rotate(Direction axis, int amount) {
        uncache();
        Matrix4f rot = new Matrix4f().rotate((float) (Math.PI / 2 * amount), v(axis));
        
        up = transform(up, rot);
        forward = transform(forward, rot);
    }

    private static Vector3f v(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    private static Direction vprime(Vector3f dir) {
        float x = Math.abs(dir.x);
        float y = Math.abs(dir.y);
        float z = Math.abs(dir.z);

        if (x >= y && x >= z) return dir.x > 0 ? Direction.EAST : Direction.WEST;
        if (y >= x && y >= z) return dir.y > 0 ? Direction.UP : Direction.DOWN;
        return dir.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static Direction transform(Direction dir, Matrix4f transform) {
        return vprime(v(dir).mulTransposeDirection(transform));
    }

    public MMTransform copy() {
        MMTransform next = new MMTransform();
        next.flipX = this.flipX;
        next.flipY = this.flipY;
        next.flipZ = this.flipZ;
        next.forward = this.forward;
        next.up = this.up;
        return next;
    }

    // Value equality over the orientation fields only (rotation is a transient cache). Required so
    // MMConfig.equals works across copies — otherwise the client re-analyses and re-bakes the ghost
    // preview every frame.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MMTransform that = (MMTransform) o;
        return flipX == that.flipX && flipY == that.flipY && flipZ == that.flipZ
                && forward == that.forward && up == that.up;
    }

    @Override
    public int hashCode() {
        return Objects.hash(flipX, flipY, flipZ, forward, up);
    }
}
