package dev.wp.matter_manipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.wp.matter_manipulator.common.building.PendingBlock;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders translucent ghost-block hints for all manipulator modes, like the 1.7.10
 * drawHints. The pending-block list is baked into a cached VertexBuffer and redrawn
 * each frame; rebuilds happen when the analysis changes, the player moves, or status
 * hints arrive. Blocks flagged as build warnings/errors are tinted yellow/red.
 */
@OnlyIn(Dist.CLIENT)
public class GhostBlockRenderer {

    private static final float GHOST_ALPHA = 0.35f;
    /**
     * Hard cap on baked block models (the configurable maxHints still applies if lower).
     * The original's 1M hint cap drew simple boxes; full block models are far heavier.
     */
    private static final int HARD_BLOCK_CAP = 32768;
    private static final int MAX_REMOVAL_BOXES = 4096;
    /** Rebake when the player moves this far (blocks), for range culling. */
    private static final double REBUILD_MOVE_DIST_SQ = 2 * 2;

    private static final int TINT_WARNING = 0xFFAA00;
    private static final int TINT_ERROR = 0xFF5555;
    private static final int TINT_NONE = 0xFFFFFF;

    @Nullable
    private static VertexBuffer vbo;
    /**
     * Reused across rebuilds: a BufferBuilder's backing memory is malloc'd native
     * memory that is never freed, so allocating one per rebake leaks it.
     */
    @Nullable
    private static BufferBuilder builder;
    private static boolean empty = true;
    private static BlockPos anchor = BlockPos.ZERO;

    private static List<PendingBlock> lastBlocks = null;
    private static BlockPos lastOrigin = null;
    private static Vec3 lastPlayerPos = null;
    private static boolean dirty = true;

    /** Positions (BlockPos.asLong) flagged by the last build's status packet. */
    private static final LongSet errorHints = new LongOpenHashSet();
    private static final LongSet warningHints = new LongOpenHashSet();
    private static long statusExpiresAt = 0;

    /** Air-spec hints (block removals), drawn as red boxes since block models can't show them. */
    private static final List<BlockPos> removalBoxes = new ArrayList<>();

    public static void invalidate() {
        dirty = true;
    }

    /** Step 7 entry point: build status hints with expiration. */
    public static void setStatusHints(long[] errors, long[] warnings) {
        errorHints.clear();
        warningHints.clear();
        for (long pos : errors) errorHints.add(pos);
        for (long pos : warnings) warningHints.add(pos);
        int seconds = MMModConfig.STATUS_EXPIRATION.get();
        statusExpiresAt = seconds <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + seconds * 1000L;
        dirty = true;
    }

    public static boolean hasStatusHintAt(long pos) {
        return errorHints.contains(pos) || warningHints.contains(pos);
    }

    /**
     * Updates the baked buffer if needed. {@code origin} is added to each block's relPos
     * (pass null when positions are already absolute, i.e. all modes except COPYING/MOVING).
     * {@code range} is the tier range in blocks, or negative for unlimited.
     */
    public static void submit(List<PendingBlock> blocks, @Nullable BlockPos origin,
                              ClientLevel level, Player player, int range) {
        if (statusExpiresAt != 0 && System.currentTimeMillis() > statusExpiresAt) {
            errorHints.clear();
            warningHints.clear();
            statusExpiresAt = 0;
            dirty = true;
        }

        boolean moved = lastPlayerPos == null
                || player.position().distanceToSqr(lastPlayerPos) > REBUILD_MOVE_DIST_SQ;
        if (!dirty && blocks == lastBlocks && Objects.equals(origin, lastOrigin) && !moved) return;

        lastBlocks = blocks;
        lastOrigin = origin;
        lastPlayerPos = player.position();
        dirty = false;

        rebuild(blocks, origin, level, player, range);
    }

    private static void rebuild(List<PendingBlock> blocks, @Nullable BlockPos origin,
                                ClientLevel level, Player player, int range) {
        removalBoxes.clear();
        empty = true;
        if (blocks.isEmpty()) return;

        int maxHints = Math.min(MMModConfig.MAX_HINTS.get(), HARD_BLOCK_CAP);
        double rangeSq = range < 0 ? Double.MAX_VALUE : (double) range * range;
        anchor = player.blockPosition();

        var brd = Minecraft.getInstance().getBlockRenderer();
        if (builder == null) builder = new BufferBuilder(786432);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        var consumer = new GhostConsumer(builder, GHOST_ALPHA);
        var pose = new PoseStack();

        int count = 0;
        for (var pb : blocks) {
            if (count >= maxHints) break;

            BlockPos pos = origin != null ? origin.offset(pb.relPos) : pb.relPos;
            if (rangeSq != Double.MAX_VALUE
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;
            if (!level.isLoaded(pos)) continue;

            BlockState target = pb.spec.state;
            BlockState current = level.getBlockState(pos);

            if (target.isAir()) {
                // Removal hint: no model to draw, use a red box instead (StructureLib substitute)
                if (!current.isAir() && removalBoxes.size() < MAX_REMOVAL_BOXES) removalBoxes.add(pos);
                continue;
            }
            if (current == target) continue; // already correct, like the original drawHints skip

            long key = pos.asLong();
            consumer.setTint(errorHints.contains(key) ? TINT_ERROR
                    : warningHints.contains(key) ? TINT_WARNING : TINT_NONE);

            pose.pushPose();
            pose.translate(pos.getX() - anchor.getX(), pos.getY() - anchor.getY(), pos.getZ() - anchor.getZ());
            // null render type = all model layers
            brd.renderBatched(target, pos, level, pose, consumer, false, level.getRandom(), ModelData.EMPTY, null);
            pose.popPose();
            count++;
        }

        var rendered = builder.end();
        if (count == 0) {
            rendered.release();
            return;
        }

        if (vbo == null) vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
        empty = false;
    }

    /**
     * Draws the baked ghosts. The PoseStack must already be translated by -camPos.
     * EXCHANGING always draws on top (the targets are inside existing blocks),
     * matching the original's drawOnTop logic.
     */
    public static void render(PoseStack ps, PlaceMode mode) {
        boolean onTop = MMModConfig.HINTS_ON_TOP.get() || mode == PlaceMode.EXCHANGING;

        if (!empty && vbo != null) {
            var rt = onTop ? MMRenderTypes.GHOST_BLOCKS_ON_TOP : MMRenderTypes.GHOST_BLOCKS;
            ps.pushPose();
            ps.translate(anchor.getX(), anchor.getY(), anchor.getZ());
            rt.setupRenderState();
            vbo.bind();
            vbo.drawWithShader(ps.last().pose(), RenderSystem.getProjectionMatrix(),
                    GameRenderer.getRendertypeTranslucentShader());
            VertexBuffer.unbind();
            rt.clearRenderState();
            ps.popPose();
        }

        for (var pos : removalBoxes) {
            BoxRenderer.drawFilledBox(ps,
                    pos.getX() + 0.05, pos.getY() + 0.05, pos.getZ() + 0.05,
                    pos.getX() + 0.95, pos.getY() + 0.95, pos.getZ() + 0.95,
                    1f, 0.33f, 0.33f, 0.35f);
        }
    }

    /** Standalone error/warning boxes for status positions not covered by pending blocks. */
    public static void renderStatusBoxes(PoseStack ps) {
        if (errorHints.isEmpty() && warningHints.isEmpty()) return;

        var iter = errorHints.longIterator();
        while (iter.hasNext()) drawStatusBox(ps, iter.nextLong(), 1f, 0.33f, 0.33f);
        iter = warningHints.longIterator();
        while (iter.hasNext()) drawStatusBox(ps, iter.nextLong(), 1f, 0.67f, 0f);
    }

    private static void drawStatusBox(PoseStack ps, long packed, float r, float g, float b) {
        BlockPos pos = BlockPos.of(packed);
        BoxRenderer.drawFilledBox(ps,
                pos.getX() + 0.1, pos.getY() + 0.1, pos.getZ() + 0.1,
                pos.getX() + 0.9, pos.getY() + 0.9, pos.getZ() + 0.9,
                r, g, b, 0.4f);
    }

    /**
     * Multiplies vertex colors by a tint and forces a fixed alpha, making the
     * block model translucent like the original's hint rendering.
     */
    private static final class GhostConsumer implements VertexConsumer {

        private final VertexConsumer inner;
        private final int a255;
        private float tr = 1f, tg = 1f, tb = 1f;

        GhostConsumer(VertexConsumer inner, float alphaMult) {
            this.inner = inner;
            this.a255 = Math.max(1, Math.min(255, (int) (255f * alphaMult)));
        }

        void setTint(int rgb) {
            tr = ((rgb >> 16) & 0xFF) / 255f;
            tg = ((rgb >> 8) & 0xFF) / 255f;
            tb = (rgb & 0xFF) / 255f;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            inner.color((int) (r * tr), (int) (g * tg), (int) (b * tb), a255);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            inner.defaultColor((int) (r * tr), (int) (g * tg), (int) (b * tb), a255);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }
}
