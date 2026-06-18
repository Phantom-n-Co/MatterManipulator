package dev.wp.matter_manipulator.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

/**
 * Utility for drawing translucent animated selection boxes.
 *
 * <p>Draws through a vanilla {@code position_color} {@link RenderType} (not a custom GL shader) so it
 * renders both normally and under an Iris/Oculus shaderpack — Iris patches the vanilla core shaders, so
 * geometry routed through them participates in the pack's pipeline. The fancybox "travelling wave" shimmer
 * is baked per-vertex on the CPU across a subdivided grid (a custom GLSL program can't run under a pack).
 */
@OnlyIn(Dist.CLIENT)
public class BoxRenderer {
    // Grid density for the per-vertex shimmer: steps per block along an edge, capped so huge selections
    // stay cheap (the wave just undersamples on very large boxes). ponytail: bump if the wave looks faceted.
    private static final float STEPS_PER_BLOCK = 4f;
    private static final int MAX_STEPS = 12;

    /**
     * Draws a translucent, animated filled AABB.
     * Callers must have the PoseStack already translated by -camPos before calling.
     */
    public static void drawFilledBox(
            PoseStack ps,
            double x0,
            double y0,
            double z0,
            double x1,
            double y1,
            double z1,
            float r,
            float g,
            float b,
            float a) {
        // Expand slightly so the fill sits just outside the wireframe
        x0 -= 0.002;
        y0 -= 0.002;
        z0 -= 0.002;
        x1 += 0.002;
        y1 += 0.002;
        z1 += 0.002;

        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        float dX = fx1 - fx0, dY = fy1 - fy0, dZ = fz1 - fz0;

        // Phase of the shimmer (0‥2.5 s period), matching the old fancybox.fsh.
        float t = (System.currentTimeMillis() % 2500) / 1000f / 2.5f * 2f * (float) Math.PI;

        Matrix4f pose = ps.last().pose();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(MMRenderType.FANCY_BOX);

        // Only draw the near walls, never the far ones.
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        // Each face = origin corner + two edge vectors, with UVs forming a continuous unwrap across the box.
        // spotless:off
        // ── bottom (y-) ──────────────────────────────────────────────────────────
        if (cam.y <= fy0) face(vc, pose, t, r, g, b, a,
                fx0, fy0, fz0, 0, 0,      dX, 0, 0, dX, 0,      0, 0, dZ, 0, dZ);
        // ── top (y+) ─────────────────────────────────────────────────────────────
        if (cam.y >= fy1) face(vc, pose, t, r, g, b, a,
                fx0, fy1, fz0, dY, 0,     0, 0, dZ, 0, dZ,      dX, 0, 0, dX, 0);
        // ── west (x-) ────────────────────────────────────────────────────────────
        if (cam.x <= fx0) face(vc, pose, t, r, g, b, a,
                fx0, fy0, fz0, 0, 0,      0, 0, dZ, 0, dZ,      0, dY, 0, dY, 0);
        // ── east (x+) ────────────────────────────────────────────────────────────
        if (cam.x >= fx1) face(vc, pose, t, r, g, b, a,
                fx1, fy1, fz1, dX + dY, dZ,   0, -dY, 0, -dY, 0,   0, 0, -dZ, 0, -dZ);
        // ── north (z-) ───────────────────────────────────────────────────────────
        if (cam.z <= fz0) face(vc, pose, t, r, g, b, a,
                fx0, fy0, fz0, 0, 0,      dX, 0, 0, dX, 0,      0, dY, 0, 0, dY);
        // ── south (z+) ───────────────────────────────────────────────────────────
        if (cam.z >= fz1) face(vc, pose, t, r, g, b, a,
                fx0, fy0, fz1, dZ, 0,     0, dY, 0, 0, dY,      dX, 0, 0, dX, 0);
        // spotless:on

        buffers.endBatch(MMRenderType.FANCY_BOX);
    }

    /** Subdivides one face (origin + two edge vectors, each with a UV delta) into a grid of quads. */
    // spotless:off
    private static void face(
            VertexConsumer vc, Matrix4f pose, float t, float r, float g, float b, float a,
            float ox, float oy, float oz, float ou, float ov,
            float e1x, float e1y, float e1z, float e1u, float e1v,
            float e2x, float e2y, float e2z, float e2u, float e2v) {
        int n1 = steps(e1x, e1y, e1z);
        int n2 = steps(e2x, e2y, e2z);
        for (int i = 0; i < n1; i++) {
            float s0 = (float) i / n1, s1 = (float) (i + 1) / n1;
            for (int j = 0; j < n2; j++) {
                float q0 = (float) j / n2, q1 = (float) (j + 1) / n2;
                corner(vc, pose, t, r, g, b, a, ox, oy, oz, ou, ov, e1x, e1y, e1z, e1u, e1v, e2x, e2y, e2z, e2u, e2v, s0, q0);
                corner(vc, pose, t, r, g, b, a, ox, oy, oz, ou, ov, e1x, e1y, e1z, e1u, e1v, e2x, e2y, e2z, e2u, e2v, s1, q0);
                corner(vc, pose, t, r, g, b, a, ox, oy, oz, ou, ov, e1x, e1y, e1z, e1u, e1v, e2x, e2y, e2z, e2u, e2v, s1, q1);
                corner(vc, pose, t, r, g, b, a, ox, oy, oz, ou, ov, e1x, e1y, e1z, e1u, e1v, e2x, e2y, e2z, e2u, e2v, s0, q1);
            }
        }
    }

    /** Emits one grid vertex at parameters (s, q), with the shimmer brightness baked into its colour. */
    private static void corner(
            VertexConsumer vc, Matrix4f pose, float t, float r, float g, float b, float a,
            float ox, float oy, float oz, float ou, float ov,
            float e1x, float e1y, float e1z, float e1u, float e1v,
            float e2x, float e2y, float e2z, float e2u, float e2v,
            float s, float q) {
        float x = ox + s * e1x + q * e2x;
        float y = oy + s * e1y + q * e2y;
        float z = oz + s * e1z + q * e2z;
        float u = ou + s * e1u + q * e2u;
        float v = ov + s * e1v + q * e2v;
        // fancybox.fsh: k = (sin((u+v)*5 + t)+1)/2 + 0.25; colour = mix(c*0.25, c, k) = c*(0.25 + 0.75k).
        float k = ((float) Math.sin((u + v) * 5f + t) + 1f) * 0.5f + 0.25f;
        float f = 0.25f + 0.75f * k;
        vc.vertex(pose, x, y, z)
                .color(Math.min(1f, r * f), Math.min(1f, g * f), Math.min(1f, b * f), Math.min(1f, a * f))
                .endVertex();
    }
    // spotless:on

    private static int steps(float ex, float ey, float ez) {
        double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
        return Math.max(1, Math.min(MAX_STEPS, (int) Math.ceil(len * STEPS_PER_BLOCK)));
    }

    /** Translucent, double-sided, no depth-write RenderType on the vanilla {@code position_color} shader. */
    private static final class MMRenderType extends RenderType {
        private MMRenderType(
                String name,
                VertexFormat fmt,
                VertexFormat.Mode mode,
                int size,
                boolean crumbling,
                boolean sort,
                Runnable setup,
                Runnable clear) {
            super(name, fmt, mode, size, crumbling, sort, setup, clear);
        }

        static final RenderType FANCY_BOX = create(
                "matter_manipulator:fancy_box",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
