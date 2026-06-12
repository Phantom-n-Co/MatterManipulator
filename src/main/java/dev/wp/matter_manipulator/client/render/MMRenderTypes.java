package dev.wp.matter_manipulator.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Render types for ghost-block hint rendering. Two variants of vanilla translucent
 * block rendering: one with normal depth testing, one drawn on top of terrain
 * (the "hintsOnTop" config).
 */
@OnlyIn(Dist.CLIENT)
public class MMRenderTypes extends RenderType {

    // Never instantiated; subclassing only grants access to the protected state shards.
    private MMRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    public static final RenderType GHOST_BLOCKS = create(
            "matter_manipulator_ghost",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false));

    public static final RenderType GHOST_BLOCKS_ON_TOP = create(
            "matter_manipulator_ghost_on_top",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 786432, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
}
