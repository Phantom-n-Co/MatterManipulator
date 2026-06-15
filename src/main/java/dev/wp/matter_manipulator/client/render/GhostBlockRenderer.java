package dev.wp.matter_manipulator.client.render;

import dev.wp.matter_manipulator.common.building.PendingBlock;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Renders translucent ghost-block hints for all manipulator modes.
 *
 * <p>All preview blocks are baked (with every face, no neighbor culling) into a single vertex buffer
 * whose quads are sorted back-to-front from the camera — a painter's-algorithm translucent render,
 * matching GTNH's look: front blocks read as in-front, interior blocks show through faintly, and there
 * are no per-chunk ordering seams. The bake is time-sliced across frames (the previous preview stays
 * visible until the new one is ready), and the buffer is cheaply re-sorted (index-only) when the
 * camera moves. Blocks flagged as build warnings/errors are tinted yellow/red.
 */
@OnlyIn(Dist.CLIENT)
public class GhostBlockRenderer {
    private static final float GHOST_ALPHA = 0.5f;
    /** Ghost models render at this fraction of full size, centered in their cell, so gaps are visible. */
    private static final float GHOST_SCALE = 0.75f;

    private static final int MAX_REMOVAL_BOXES = 4096;
    /**
     * Rebake when the player moves this far (blocks), for range culling. Only matters when a finite
     * range is set; movement otherwise never changes which blocks are baked.
     */
    private static final double REBUILD_MOVE_DIST_SQ = 8 * 8;
    /** Re-sort the camera-distance-ordered buffer when the camera moves at least this far (blocks²). */
    private static final double RESORT_DIST_SQ = 2.0;

    private static final int TINT_WARNING = 0xFFAA00;
    private static final int TINT_ERROR = 0xFF5555;
    private static final int TINT_NONE = 0xFFFFFF;

    /** Blocks scanned (grouped) per frame; cheap (world reads only), so a large slice is fine. */
    private static final int GROUP_BUDGET = 16384;
    /** Blocks baked into the buffer per frame; renderBatched is heavy, so this slice is smaller. */
    private static final int BAKE_BUDGET = 2048;

    /** Displayed buffer: every face of the preview, sorted back-to-front for correct translucency. */
    @Nullable
    private static VertexBuffer vbo;
    /** Captured after each bake/sort so the buffer can be re-sorted (index-only) on camera movement. */
    @Nullable
    private static BufferBuilder.SortState sortState;

    @Nullable
    private static AABB bounds;

    private static BlockPos anchor = BlockPos.ZERO;
    /** Camera position the buffer is currently sorted for. */
    @Nullable
    private static Vec3 lastSortPos;

    /**
     * Reused across rebuilds: a BufferBuilder's backing memory is malloc native memory that is never
     * freed, so allocating one per rebake leaks it. It also retains the last baked vertices, which the
     * index-only re-sort reuses.
     */
    @Nullable
    private static BufferBuilder builder;

    /** In-progress time-sliced bake; the old {@link #vbo} stays visible until it finishes. */
    @Nullable
    private static BakeJob currentJob;
    /** Latest inputs awaiting a (re)build, captured by {@link #submit}. */
    private static boolean needsRebuild = false;

    private static List<PendingBlock> pendingBlocks = null;
    private static BlockPos pendingOrigin = null;
    private static ClientLevel pendingLevel = null;
    private static Player pendingPlayer = null;
    private static int pendingRange = -1;
    private static Vec3 pendingCamPos = null;

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
     * Updates the baked buffer if needed. {@code origin} is added to each block's relPos (pass null
     * when positions are already absolute, i.e., all modes except COPYING/MOVING). {@code range} is
     * the tier range in blocks, or negative for unlimited. {@code camPos} is the eye position, used to
     * sort the translucent geometry.
     */
    public static void submit(
            List<PendingBlock> blocks,
            @Nullable BlockPos origin,
            ClientLevel level,
            Player player,
            int range,
            Vec3 camPos) {
        if (statusExpiresAt != 0 && System.currentTimeMillis() > statusExpiresAt) {
            errorHints.clear();
            warningHints.clear();
            statusExpiresAt = 0;
            dirty = true;
        }

        // Movement only changes the baked output when a finite range culls blocks; with unlimited
        // range, walking around must not force a rebake of every model.
        boolean moved = range >= 0
                && (lastPlayerPos == null || player.position().distanceToSqr(lastPlayerPos) > REBUILD_MOVE_DIST_SQ);
        if (dirty || blocks != lastBlocks || !Objects.equals(origin, lastOrigin) || moved) {
            lastBlocks = blocks;
            lastOrigin = origin;
            lastPlayerPos = player.position();
            dirty = false;

            // Capture the latest inputs; the actual bake is time-sliced over the next frames.
            pendingBlocks = blocks;
            pendingOrigin = origin;
            pendingLevel = level;
            pendingPlayer = player;
            pendingRange = range;
            pendingCamPos = camPos;
            needsRebuild = true;
        }

        driveBake();

        // Re-sort the existing buffer when the camera has translated (rotation doesn't change
        // distances). Skipped while a bake is running, since that owns the shared builder.
        if (currentJob == null
                && vbo != null
                && sortState != null
                && (lastSortPos == null || camPos.distanceToSqr(lastSortPos) > RESORT_DIST_SQ)) {
            resort(camPos);
        }
    }

    /**
     * Advances any in-progress bake by one frame's budget, and starts a new one when inputs have
     * changed and no bake is running. The previous baked preview stays visible throughout.
     */
    private static void driveBake() {
        if (currentJob == null) {
            if (!needsRebuild) return;
            needsRebuild = false;

            if (pendingBlocks == null || pendingBlocks.isEmpty()) {
                disposeBuffer();
                removalBoxes.clear();
                return;
            }
            currentJob = new BakeJob(pendingBlocks, pendingOrigin, pendingLevel, pendingPlayer, pendingRange, pendingCamPos);
        }

        if (builder == null) builder = new BufferBuilder(2097152);
        var brd = Minecraft.getInstance().getBlockRenderer();
        var consumer = new GhostConsumer(builder, GHOST_ALPHA);

        currentJob.advance(brd, consumer);

        if (currentJob.done()) {
            commit(currentJob);
            currentJob = null;
        }
    }

    /** Swaps a finished job's buffer in for the live one. */
    private static void commit(BakeJob job) {
        disposeBuffer();
        vbo = job.vbo;
        sortState = job.sortState;
        bounds = job.bounds;
        anchor = job.jobAnchor;
        lastSortPos = job.camPos;
        removalBoxes.clear();
        removalBoxes.addAll(job.removalBoxes);
    }

    private static void disposeBuffer() {
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
        sortState = null;
        bounds = null;
    }

    /** Re-orders the existing buffer's quads for a new camera position without re-tessellating. */
    private static void resort(Vec3 camPos) {
        if (builder == null) return;
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        builder.restoreSortState(sortState);
        builder.setQuadSorting(
                VertexSorting.byDistance((float) (camPos.x - anchor.getX()), (float) (camPos.y - anchor.getY()), (float) (camPos.z - anchor.getZ())));
        sortState = builder.getSortState();
        var rb = builder.end();
        vbo.bind();
        vbo.upload(rb);
        VertexBuffer.unbind();
        lastSortPos = camPos;
    }

    /**
     * Draws the baked, back-to-front-sorted preview. The PoseStack must already be translated by
     * -camPos. EXCHANGING always draws on top since its targets are inside existing blocks.
     */
    public static void render(PoseStack ps, PlaceMode mode, Frustum frustum) {
        if (vbo != null && (bounds == null || frustum.isVisible(bounds))) {
            boolean onTop = MMModConfig.HINTS_ON_TOP.get() || mode == PlaceMode.EXCHANGING;
            var rt = onTop ? MMRenderTypes.GHOST_BLOCKS_ON_TOP : MMRenderTypes.GHOST_BLOCKS;
            ps.pushPose();
            ps.translate(anchor.getX(), anchor.getY(), anchor.getZ());
            rt.setupRenderState();
            vbo.bind();
            vbo.drawWithShader(
                    ps.last().pose(),
                    RenderSystem.getProjectionMatrix(),
                    GameRenderer.getRendertypeTranslucentShader());
            VertexBuffer.unbind();
            rt.clearRenderState();
            ps.popPose();
        }

        for (var pos : removalBoxes) {
            if (!frustum.isVisible(new AABB(pos))) continue;
            BoxRenderer.drawFilledBox(
                    ps,
                    pos.getX() + 0.05,
                    pos.getY() + 0.05,
                    pos.getZ() + 0.05,
                    pos.getX() + 0.95,
                    pos.getY() + 0.95,
                    pos.getZ() + 0.95,
                    1f,
                    0.33f,
                    0.33f,
                    0.35f);
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
        BoxRenderer.drawFilledBox(
                ps,
                pos.getX() + 0.1,
                pos.getY() + 0.1,
                pos.getZ() + 0.1,
                pos.getX() + 0.9,
                pos.getY() + 0.9,
                pos.getZ() + 0.9,
                r,
                g,
                b,
                0.4f);
    }

    /**
     * A time-sliced bake. Phase 1 (grouping) scans the pending blocks, applying range/loaded/match
     * culling and collecting the survivors. Phase 2 (baking) tessellates every face of each survivor
     * into the shared builder, then sorts the quads back-to-front and uploads one buffer. Both phases
     * consume a bounded budget per {@link #advance} call.
     */
    private static final class BakeJob {
        private final List<PendingBlock> blocks;

        @Nullable
        private final BlockPos origin;

        private final ClientLevel level;
        private final double rangeSq;
        private final int maxHints;
        final BlockPos jobAnchor;
        final Vec3 camPos;
        private final double px, py, pz;

        // Grouping phase
        private int scanIndex = 0;
        private int accepted = 0;
        private boolean groupingDone = false;
        private final List<PendingBlock> kept = new ArrayList<>();
        final List<BlockPos> removalBoxes = new ArrayList<>();

        // Baking phase
        private boolean baking = false;
        private int bakeIndex = 0;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        private boolean done = false;

        @Nullable
        VertexBuffer vbo;

        @Nullable
        BufferBuilder.SortState sortState;

        @Nullable
        AABB bounds;

        private final PoseStack pose = new PoseStack();

        BakeJob(
                List<PendingBlock> blocks,
                @Nullable BlockPos origin,
                ClientLevel level,
                Player player,
                int range,
                Vec3 camPos) {
            this.blocks = blocks;
            this.origin = origin;
            this.level = level;
            this.rangeSq = range < 0 ? Double.MAX_VALUE : (double) range * range;
            int cfg = MMModConfig.MAX_HINTS.get();
            this.maxHints = cfg <= 0 ? Integer.MAX_VALUE : cfg;
            this.jobAnchor = player.blockPosition();
            this.camPos = camPos;
            var p = player.position();
            this.px = p.x;
            this.py = p.y;
            this.pz = p.z;
        }

        boolean done() {
            return done;
        }

        void advance(BlockRenderDispatcher brd, GhostConsumer consumer) {
            if (!groupingDone) {
                group();
            } else {
                bake(brd, consumer);
            }
        }

        private void group() {
            int budget = GROUP_BUDGET;
            int n = blocks.size();
            while (scanIndex < n && accepted < maxHints && budget-- > 0) {
                var pb = blocks.get(scanIndex++);
                BlockPos pos = origin != null ? origin.offset(pb.relPos) : pb.relPos;

                if (rangeSq != Double.MAX_VALUE) {
                    double dx = pos.getX() + 0.5 - px, dy = pos.getY() + 0.5 - py, dz = pos.getZ() + 0.5 - pz;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
                }
                if (!level.isLoaded(pos)) continue;

                BlockState target = pb.spec.state;
                BlockState current = level.getBlockState(pos);

                if (target.isAir()) {
                    // Removal hint: no model to draw, use a red box instead (StructureLib substitute)
                    if (!current.isAir() && removalBoxes.size() < MAX_REMOVAL_BOXES) removalBoxes.add(pos);
                    continue;
                }
                if (current == target) continue;

                kept.add(pb);
                accepted++;
            }

            if (scanIndex >= n || accepted >= maxHints) {
                groupingDone = true;
                if (kept.isEmpty()) done = true;
            }
        }

        private void bake(BlockRenderDispatcher brd, GhostConsumer consumer) {
            if (!baking) {
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                baking = true;
            }

            int budget = BAKE_BUDGET;
            while (bakeIndex < kept.size() && budget-- > 0) {
                var pb = kept.get(bakeIndex++);
                BlockPos pos = origin != null ? origin.offset(pb.relPos) : pb.relPos;
                BlockState target = pb.spec.state;

                long key = pos.asLong();
                consumer.setTint(
                        errorHints.contains(key) ? TINT_ERROR : warningHints.contains(key) ? TINT_WARNING : TINT_NONE);

                pose.pushPose();
                pose.translate(
                        pos.getX() - jobAnchor.getX(), pos.getY() - jobAnchor.getY(), pos.getZ() - jobAnchor.getZ());
                // Shrink each model toward its cell centre so gaps show between blocks and placement reads clearly.
                pose.translate(0.5, 0.5, 0.5);
                pose.scale(GHOST_SCALE, GHOST_SCALE, GHOST_SCALE);
                pose.translate(-0.5, -0.5, -0.5);
                // checkSides=false bakes every face (interior included) so the translucent sort can
                // show blocks behind others; null render type = all model layers.
                int before = consumer.vertexCount();
                brd.renderBatched(target, pos, level, pose, consumer, false, level.getRandom(), ModelData.EMPTY, null);

                // Blocks with a dynamic model driven by block-entity data (e.g. AE2 cable buses) bake
                // nothing from the default state; reconstruct a throwaway BE from the captured tile data
                // and retry with its model data so the real parts show.
                CompoundTag previewTile = pb.previewTileData();
                if (consumer.vertexCount() == before && previewTile != null) {
                    ModelData md = reconstructModelData(pos, target, previewTile);
                    if (md != ModelData.EMPTY) {
                        brd.renderBatched(target, pos, level, pose, consumer, false, level.getRandom(), md, null);
                    }
                }

                // Still nothing (block-entity-renderer-only blocks like chests): a textured cube so it
                // at least appears in the preview.
                if (consumer.vertexCount() == before) bakeFallbackCube(brd, consumer, target);
                pose.popPose();

                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            if (bakeIndex >= kept.size()) finish();
        }

        private void finish() {
            builder.setQuadSorting(VertexSorting.byDistance(
                    (float) (camPos.x - jobAnchor.getX()), (float) (camPos.y - jobAnchor.getY()), (float) (camPos.z - jobAnchor.getZ())));
            sortState = builder.getSortState();

            var rb = builder.endOrDiscardIfEmpty();
            if (rb == null) {
                sortState = null;
                done = true;
                return;
            }

            vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(rb);
            VertexBuffer.unbind();
            bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            done = true;
        }

        /**
         * Rebuilds a throwaway block entity from captured NBT and returns its model data, so blocks
         * with block-entity-driven dynamic models (AE2 cable buses, etc.) can render their real parts.
         * Returns {@link ModelData#EMPTY} if the block has no such data or anything goes wrong.
         */
        private ModelData reconstructModelData(
                BlockPos pos, BlockState target, CompoundTag tileData) {
            try {
                var be = BlockEntity.loadStatic(pos, target, tileData);
                if (be == null) return ModelData.EMPTY;
                be.setLevel(level);
                return be.getModelData();
            } catch (Exception e) {
                return ModelData.EMPTY;
            }
        }

        /** Emits a [0,1] cube textured with the block's particle sprite (the current pose scales it). */
        private void bakeFallbackCube(BlockRenderDispatcher brd, GhostConsumer c, BlockState target) {
            TextureAtlasSprite sprite = brd.getBlockModel(target).getParticleIcon(ModelData.EMPTY);
            var p = pose.last();
            Matrix4f mat = p.pose();
            Matrix3f nm = p.normal();
            float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

            face(c, mat, nm, u0, u1, v0, v1, 0, -1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1); // down
            face(c, mat, nm, u0, u1, v0, v1, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0); // up
            face(c, mat, nm, u0, u1, v0, v1, 0, 0, -1, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0); // north
            face(c, mat, nm, u0, u1, v0, v1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1); // south
            face(c, mat, nm, u0, u1, v0, v1, -1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0); // west
            face(c, mat, nm, u0, u1, v0, v1, 1, 0, 0, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1); // east
        }

        private static void face(
                GhostConsumer c,
                Matrix4f mat,
                Matrix3f nm,
                float u0,
                float u1,
                float v0,
                float v1,
                float nx,
                float ny,
                float nz,
                float ax,
                float ay,
                float az,
                float bx,
                float by,
                float bz,
                float cx,
                float cy,
                float cz,
                float dx,
                float dy,
                float dz) {
            int light = 0xF000F0;
            c.vertex(mat, ax, ay, az)
                    .color(255, 255, 255, 255)
                    .uv(u0, v0)
                    .uv2(light)
                    .normal(nm, nx, ny, nz)
                    .endVertex();
            c.vertex(mat, bx, by, bz)
                    .color(255, 255, 255, 255)
                    .uv(u1, v0)
                    .uv2(light)
                    .normal(nm, nx, ny, nz)
                    .endVertex();
            c.vertex(mat, cx, cy, cz)
                    .color(255, 255, 255, 255)
                    .uv(u1, v1)
                    .uv2(light)
                    .normal(nm, nx, ny, nz)
                    .endVertex();
            c.vertex(mat, dx, dy, dz)
                    .color(255, 255, 255, 255)
                    .uv(u0, v1)
                    .uv2(light)
                    .normal(nm, nx, ny, nz)
                    .endVertex();
        }
    }

    /** Multiplies vertex colors by a tint and forces a fixed alpha, making block models translucent. */
    private static final class GhostConsumer implements VertexConsumer {
        private final VertexConsumer inner;
        private final int a255;
        private float tr = 1f, tg = 1f, tb = 1f;
        private int verts = 0;

        GhostConsumer(VertexConsumer inner, float alphaMult) {
            this.inner = inner;
            this.a255 = Math.max(1, Math.min(255, (int) (255f * alphaMult)));
        }

        /** Running total of emitted vertices, used to detect blocks that baked no geometry. */
        int vertexCount() {
            return verts;
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
            verts++;
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
