package dev.wp.matter_manipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import lombok.Setter;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import javax.annotation.Nullable;

/** Utility for drawing translucent animated selection boxes via the fancybox shader. */
@OnlyIn(Dist.CLIENT)
public class BoxRenderer {

    /**
     * -- SETTER --
     * Called from RegisterShadersEvent once the shader is compiled and linked.
     */
    @Setter
    @Nullable
    private static ShaderInstance fancyBoxShader;

    // ── Filled animated box ───────────────────────────────────────────────────────

    /**
     * Draws a translucent, animated filled AABB using the fancybox shader.
     * Callers must have the PoseStack already translated by -camPos before calling.
     */
    // spotless:off
    public static void drawFilledBox(PoseStack ps,
                                     double x0, double y0, double z0,
                                     double x1, double y1, double z1,
                                     float r, float g, float b, float a) {
        if (fancyBoxShader == null) return;

        // Expand slightly so the fill sits just outside the wireframe
        x0 -= 0.002; y0 -= 0.002; z0 -= 0.002;
        x1 += 0.002; y1 += 0.002; z1 += 0.002;

        float fx0 = (float) x0, fy0 = (float) y0, fz0 = (float) z0;
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        float dX = fx1 - fx0, dY = fy1 - fy0, dZ = fz1 - fz0;
        Matrix4f mat = ps.last().pose();

        // Upload time uniform (0‥2.5 s period)
        var timeUniform = fancyBoxShader.getUniform("time");
        if (timeUniform != null) timeUniform.set((System.currentTimeMillis() % 2500) / 1000f);

        RenderSystem.setShader(() -> fancyBoxShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        // Each face uses UV coords that form a continuous unwrap across the whole box.

        // ── bottom (y-)  UV = (localX, localZ) ───────────────────────────────────
        buf.vertex(mat, fx0, fy0, fz0).color(r,g,b,a).uv(0,   0  ).endVertex();
        buf.vertex(mat, fx1, fy0, fz0).color(r,g,b,a).uv(dX,  0  ).endVertex();
        buf.vertex(mat, fx1, fy0, fz1).color(r,g,b,a).uv(dX,  dZ ).endVertex();
        buf.vertex(mat, fx0, fy0, fz1).color(r,g,b,a).uv(0,   dZ ).endVertex();

        // ── top (y+)     UV = (dY + localX, localZ) ──────────────────────────────
        buf.vertex(mat, fx0, fy1, fz0).color(r,g,b,a).uv(dY,      0  ).endVertex();
        buf.vertex(mat, fx0, fy1, fz1).color(r,g,b,a).uv(dY,      dZ ).endVertex();
        buf.vertex(mat, fx1, fy1, fz1).color(r,g,b,a).uv(dY + dX, dZ ).endVertex();
        buf.vertex(mat, fx1, fy1, fz0).color(r,g,b,a).uv(dY + dX, 0  ).endVertex();

        // ── west (x-)    UV = (localY, localZ) ───────────────────────────────────
        buf.vertex(mat, fx0, fy0, fz0).color(r,g,b,a).uv(0,  0  ).endVertex();
        buf.vertex(mat, fx0, fy0, fz1).color(r,g,b,a).uv(0,  dZ ).endVertex();
        buf.vertex(mat, fx0, fy1, fz1).color(r,g,b,a).uv(dY, dZ ).endVertex();
        buf.vertex(mat, fx0, fy1, fz0).color(r,g,b,a).uv(dY, 0  ).endVertex();

        // ── east (x+)    UV = (dX + localY, localZ) ──────────────────────────────
        buf.vertex(mat, fx1, fy1, fz1).color(r,g,b,a).uv(dX + dY, dZ ).endVertex();
        buf.vertex(mat, fx1, fy0, fz1).color(r,g,b,a).uv(dX,      dZ ).endVertex();
        buf.vertex(mat, fx1, fy0, fz0).color(r,g,b,a).uv(dX,      0  ).endVertex();
        buf.vertex(mat, fx1, fy1, fz0).color(r,g,b,a).uv(dX + dY, 0  ).endVertex();

        // ── north (z-)   UV = (localX, localY) ───────────────────────────────────
        buf.vertex(mat, fx0, fy0, fz0).color(r,g,b,a).uv(0,  0  ).endVertex();
        buf.vertex(mat, fx1, fy0, fz0).color(r,g,b,a).uv(dX, 0  ).endVertex();
        buf.vertex(mat, fx1, fy1, fz0).color(r,g,b,a).uv(dX, dY ).endVertex();
        buf.vertex(mat, fx0, fy1, fz0).color(r,g,b,a).uv(0,  dY ).endVertex();

        // ── south (z+)   UV = (dZ + localX, localY) ──────────────────────────────
        buf.vertex(mat, fx0, fy0, fz1).color(r,g,b,a).uv(dZ,      0  ).endVertex();
        buf.vertex(mat, fx0, fy1, fz1).color(r,g,b,a).uv(dZ,      dY ).endVertex();
        buf.vertex(mat, fx1, fy1, fz1).color(r,g,b,a).uv(dZ + dX, dY ).endVertex();
        buf.vertex(mat, fx1, fy0, fz1).color(r,g,b,a).uv(dZ + dX, 0  ).endVertex();

        BufferUploader.drawWithShader(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    // spotless:on
}
