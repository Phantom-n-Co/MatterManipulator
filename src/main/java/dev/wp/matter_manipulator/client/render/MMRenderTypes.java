package dev.wp.matter_manipulator.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Render types for ghost-block hint rendering. The preview geometry is sorted back-to-front on the
 * CPU (painter's algorithm), so both variants disable depth-writes and back-face culling and rely on
 * the sort for correct translucency: one keeps depth-testing (occluded by terrain), one ignores it
 * (drawn on top of terrain — the "hintsOnTop" config).
 */
@OnlyIn(Dist.CLIENT)
public class MMRenderTypes extends RenderType {

    // Never instantiated; subclassing only grants access to the protected state shards.
    private MMRenderTypes(
            String name,
            VertexFormat format,
            VertexFormat.Mode mode,
            int bufferSize,
            boolean affectsCrumbling,
            boolean sortOnUpload,
            Runnable setup,
            Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static final RenderType GHOST_BLOCKS = create(
            "matter_manipulator_ghost",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    public static final RenderType GHOST_BLOCKS_ON_TOP = create(
            "matter_manipulator_ghost_on_top",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
}
