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
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Renders translucent ghost-block hints for all manipulator modes. The pending-block
 * list is baked into a cached VertexBuffer and redrawn each frame; rebuilds happen
 * when the analysis changes, the player moves, or status hints arrive. Blocks flagged
 * as build warnings/errors are tinted yellow/red.
 */
@OnlyIn(Dist.CLIENT)
public class GhostBlockRenderer {

    private static final float GHOST_ALPHA = 0.75f;
    /**
     * Memory-safety ceiling on baked block models, independent of the configurable maxHints. Caps
     * VRAM use on pathological selections; the time-sliced bake means hitting it doesn't stall.
     */
    private static final int HARD_BLOCK_CAP = 131072;
    private static final int MAX_REMOVAL_BOXES = 4096;
    /**
     * Rebake when the player moves this far (blocks), for range culling. Only matters when a
     * finite range is set; movement otherwise never changes the baked output. Kept fairly large,
     * so normal walking doesn't trigger constant full rebakes — a few blocks of lag at the range
     * boundary are invisible on a translucent preview.
     */
    private static final double REBUILD_MOVE_DIST_SQ = 8 * 8;

    private static final int TINT_WARNING = 0xFFAA00;
    private static final int TINT_ERROR = 0xFF5555;
    private static final int TINT_NONE = 0xFFFFFF;

    /** Blocks scanned (grouped) per frame; cheap (world reads only), so a large slice is fine. */
    private static final int GROUP_BUDGET = 16384;
    /** Blocks baked into VBOs per frame; renderBatched is heavy, so this slice is smaller. */
    private static final int BAKE_BUDGET = 2048;

    /** A baked region: its own VBO plus the world-space AABB used for frustum culling. */
    private record GhostChunk(VertexBuffer vbo, AABB bounds) {}

    /** The currently displayed (last fully baked) chunk set. */
    private static final List<GhostChunk> chunks = new ArrayList<>();
    /**
     * Reused across rebuilds: a BufferBuilder's backing memory is malloc native
     * memory that is never freed, so allocating one per rebake leaks it.
     */
    @Nullable
    private static BufferBuilder builder;
    private static BlockPos anchor = BlockPos.ZERO;

    /** In-progress time-sliced bake; the old {@link #chunks} stay visible until it finishes. */
    @Nullable
    private static BakeJob currentJob;
    /** Latest inputs awaiting a (re)build, captured by {@link #submit}. */
    private static boolean needsRebuild = false;
    private static List<PendingBlock> pendingBlocks = null;
    private static BlockPos pendingOrigin = null;
    private static ClientLevel pendingLevel = null;
    private static Player pendingPlayer = null;
    private static int pendingRange = -1;

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
     * Updates the baked buffer if needed. {@code origin} is added to each block's relPos
     * (pass null when positions are already absolute, i.e., all modes except COPYING/MOVING).
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

        // Movement only changes the baked output when a finite range culls blocks; with unlimited
        // range, walking around must not force a rebake of every model.
        boolean moved = range >= 0 && (lastPlayerPos == null
                || player.position().distanceToSqr(lastPlayerPos) > REBUILD_MOVE_DIST_SQ);
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
            needsRebuild = true;
        }

        driveBake();
    }

    /**
     * Advances any in-progress bake by one frame's budget, and starts a new one when inputs have
     * changed and no bake is running. The previous baked preview stays visible throughout, so a
     * large rebuild streams in over several frames instead of stalling a single frame.
     */
    private static void driveBake() {
        if (currentJob == null) {
            if (!needsRebuild) return;
            needsRebuild = false;

            if (pendingBlocks == null || pendingBlocks.isEmpty()) {
                disposeChunks();
                removalBoxes.clear();
                return;
            }
            currentJob = new BakeJob(pendingBlocks, pendingOrigin, pendingLevel, pendingPlayer, pendingRange);
        }

        if (builder == null) builder = new BufferBuilder(786432);
        var brd = Minecraft.getInstance().getBlockRenderer();
        var consumer = new GhostConsumer(builder, GHOST_ALPHA);

        currentJob.advance(brd, consumer);

        if (currentJob.done()) {
            commit(currentJob);
            currentJob = null;
        }
    }

    /** Swaps a finished job's staged chunks in for the live ones. */
    private static void commit(BakeJob job) {
        disposeChunks();
        chunks.addAll(job.staging);
        anchor = job.jobAnchor;
        removalBoxes.clear();
        removalBoxes.addAll(job.removalBoxes);
    }

    private static void disposeChunks() {
        for (var chunk : chunks) chunk.vbo().close();
        chunks.clear();
    }

    /**
     * A time-sliced bake. Phase 1 (grouping) scans the pending blocks, applying range/loaded/match
     * culling and bucketing the survivors into REGION³ cells. Phase 2 (baking) turns each cell into
     * its own VBO. Both phases consume a bounded budget per {@link #advance} call.
     */
    private static final class BakeJob {

        private final List<PendingBlock> blocks;
        @Nullable private final BlockPos origin;
        private final ClientLevel level;
        private final double rangeSq;
        private final int maxHints;
        final BlockPos jobAnchor;
        private final double px, py, pz;

        // Grouping phase
        private int scanIndex = 0;
        private int accepted = 0;
        private boolean groupingDone = false;
        private final Long2ObjectMap<List<PendingBlock>> byRegion = new Long2ObjectOpenHashMap<>();
        final List<BlockPos> removalBoxes = new ArrayList<>();

        // Baking phase
        @Nullable private List<List<PendingBlock>> regions;
        private int bakeIndex = 0;
        final List<GhostChunk> staging = new ArrayList<>();

        /** All accepted ghost target states by absolute pos, presented as neighbors for face culling. */
        private final Long2ObjectMap<BlockState> ghostStates = new Long2ObjectOpenHashMap<>();
        @Nullable private GhostNeighborGetter neighbors;

        private final PoseStack pose = new PoseStack();

        BakeJob(List<PendingBlock> blocks, @Nullable BlockPos origin,
                ClientLevel level, Player player, int range) {
            this.blocks = blocks;
            this.origin = origin;
            this.level = level;
            this.rangeSq = range < 0 ? Double.MAX_VALUE : (double) range * range;
            int cfg = MMModConfig.MAX_HINTS.get();
            this.maxHints = cfg <= 0 ? HARD_BLOCK_CAP : Math.min(cfg, HARD_BLOCK_CAP);
            this.jobAnchor = player.blockPosition();
            var p = player.position();
            this.px = p.x;
            this.py = p.y;
            this.pz = p.z;
        }

        boolean done() {
            return groupingDone && regions != null && bakeIndex >= regions.size();
        }

        void advance(net.minecraft.client.renderer.block.BlockRenderDispatcher brd, GhostConsumer consumer) {
            if (!groupingDone) {
                group();
            } else if (regions != null) {
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

                long regionKey = BlockPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
                byRegion.computeIfAbsent(regionKey, k -> new ArrayList<>()).add(pb);
                ghostStates.put(pos.asLong(), target);
                accepted++;
            }

            if (scanIndex >= n || accepted >= maxHints) {
                groupingDone = true;
                regions = new ArrayList<>(byRegion.values());
                neighbors = new GhostNeighborGetter(level, ghostStates);
            }
        }

        private void bake(net.minecraft.client.renderer.block.BlockRenderDispatcher brd, GhostConsumer consumer) {
            int budget = BAKE_BUDGET;
            while (bakeIndex < regions.size() && budget > 0) {
                var regionBlocks = regions.get(bakeIndex++);
                bakeRegion(brd, consumer, regionBlocks);
                budget -= regionBlocks.size();
            }
        }

        /** Bakes one region's blocks into a fresh VBO with a tight world-space AABB for culling. */
        private void bakeRegion(net.minecraft.client.renderer.block.BlockRenderDispatcher brd, GhostConsumer consumer,
                                List<PendingBlock> regionBlocks) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            consumer.resetVertexCount();

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

            for (var pb : regionBlocks) {
                BlockPos pos = origin != null ? origin.offset(pb.relPos) : pb.relPos;
                BlockState target = pb.spec.state;

                long key = pos.asLong();
                consumer.setTint(errorHints.contains(key) ? TINT_ERROR
                        : warningHints.contains(key) ? TINT_WARNING : TINT_NONE);

                pose.pushPose();
                pose.translate(pos.getX() - jobAnchor.getX(), pos.getY() - jobAnchor.getY(), pos.getZ() - jobAnchor.getZ());
                // checkSides=true + the ghost-neighbor getter culls faces shared with adjacent ghost
                // blocks; null render type = all model layers.
                brd.renderBatched(target, pos, neighbors, pose, consumer, true, level.getRandom(), ModelData.EMPTY, null);
                pose.popPose();

                minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
            }

            var rendered = builder.end();
            if (consumer.vertexCount() == 0) {
                rendered.release();
                return;
            }

            var vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(rendered);
            VertexBuffer.unbind();
            staging.add(new GhostChunk(vbo, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1)));
        }
    }

    /**
     * Block/light view used while baking. Returns the ghost target at any ghost position and AIR
     * everywhere else, so the vanilla face-culling pass ({@code checkSides=true}) only skips faces
     * shared between two adjacent ghost blocks — never against the real world, so shells still show
     * regardless of what's actually there. Lighting and tint delegate to the real level so ghosts
     * keep world-accurate shading.
     */
    private static final class GhostNeighborGetter implements BlockAndTintGetter {

        private static final BlockState AIR = Blocks.AIR.defaultBlockState();

        private final ClientLevel level;
        private final Long2ObjectMap<BlockState> ghosts;

        GhostNeighborGetter(ClientLevel level, Long2ObjectMap<BlockState> ghosts) {
            this.level = level;
            this.ghosts = ghosts;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return ghosts.getOrDefault(pos.asLong(), AIR);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return level.getBrightness(layer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return level.getRawBrightness(pos, amount);
        }
    }

    /**
     * Draws the baked ghosts, frustum-culling each region's VBO. The PoseStack must already be
     * translated by -camPos. EXCHANGING always draws on top since its targets are inside existing
     * blocks.
     */
    public static void render(PoseStack ps, PlaceMode mode, Frustum frustum) {
        boolean onTop = MMModConfig.HINTS_ON_TOP.get() || mode == PlaceMode.EXCHANGING;

        if (!chunks.isEmpty()) {
            var rt = onTop ? MMRenderTypes.GHOST_BLOCKS_ON_TOP : MMRenderTypes.GHOST_BLOCKS;
            var shader = GameRenderer.getRendertypeTranslucentShader();
            var proj = RenderSystem.getProjectionMatrix();
            ps.pushPose();
            ps.translate(anchor.getX(), anchor.getY(), anchor.getZ());
            var mat = ps.last().pose();
            rt.setupRenderState();
            for (var chunk : chunks) {
                if (!frustum.isVisible(chunk.bounds())) continue;
                chunk.vbo().bind();
                chunk.vbo().drawWithShader(mat, proj, shader);
            }
            VertexBuffer.unbind();
            rt.clearRenderState();
            ps.popPose();
        }

        for (var pos : removalBoxes) {
            if (!frustum.isVisible(new AABB(pos))) continue;
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

        void resetVertexCount() {
            verts = 0;
        }

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
