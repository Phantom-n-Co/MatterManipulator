package dev.wp.matter_manipulator.client.render;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.PendingBlock;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PendingAction;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.items.manipulator.ShapeType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3i;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Renders translucent bounding boxes and cable line previews
 * for the currently held matter manipulator.
 */
public class MMRenderer {
    /** Dimension string displayed above the hotbar (e.g. "10x5x8"). Empty when nothing to show. */
    public static String hudText = "";

    // Teal (selection): 0.15, 0.6, 0.75
    private static final float TR = 0.15f, TG = 0.6f, TB = 0.75f, TA = 0.75f;
    // Orange (paste): 0.75, 0.5, 0.15
    private static final float OR = 0.75f, OG = 0.5f, OB = 0.15f;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMatterManipulator)) {
            held = player.getOffhandItem();
            if (!(held.getItem() instanceof ItemMatterManipulator)) {
                hudText = "";
                return;
            }
        }

        var state = MMState.of(held);
        var cfg = getEffectiveConfig(state.getConfig(), mc);
        var camPos = mc.gameRenderer.getMainCamera().getPosition();
        var ps = event.getPoseStack();

        updateAnalysisCache(cfg, mc.level);

        // Ghost-block hints: positions are coordC-relative in COPYING/MOVING, absolute otherwise
        BlockPos ghostOrigin = (cfg.placeMode == PlaceMode.COPYING || cfg.placeMode == PlaceMode.MOVING) && cfg.coordC != null
                ? cfg.coordC.pos
                : null;
        int tierRange = ((ItemMatterManipulator) held.getItem()).getTier().range;
        GhostBlockRenderer.submit(analysisCache, ghostOrigin, mc.level, player, tierRange, camPos);

        ps.pushPose();
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        try {
            switch (cfg.placeMode) {
                case GEOMETRY, EXCHANGING -> renderAreaMode(ps, mc, cfg);
                case CABLES -> renderCableMode(ps, mc, cfg);
                case COPYING, MOVING -> renderCopyMode(ps, mc, cfg);
            }
            GhostBlockRenderer.render(ps, cfg.placeMode, event.getFrustum());
            GhostBlockRenderer.renderStatusBoxes(ps);
            renderTargetRuler(ps, mc, state.getConfig(), cfg.action);
        } finally {
            ps.popPose();
        }

        if (state.getConfig().locked && hudText != null) {
            if (hudText.isEmpty()) hudText = "LOCKED";
            else hudText += " (LOCKED)";
        }
    }

    // ── GEOMETRY / EXCHANGING ─────────────────────────────────────────────────────

    private static void renderAreaMode(PoseStack ps, Minecraft mc, MMConfig cfg) {
        if (cfg.coordA != null && cfg.coordB != null) {
            BlockPos a = cfg.coordA.pos, b = cfg.coordB.pos;

            if (cfg.placeMode == PlaceMode.GEOMETRY && cfg.shape != ShapeType.CUBE) {
                // Shape blocks themselves are shown as ghost-block hints
                hudText = analysisCache.size() + " blocks";
            } else {
                var bufSource = mc.renderBuffers().bufferSource();
                var linesBuf = bufSource.getBuffer(RenderType.lines());

                double x0 = Math.min(a.getX(), b.getX());
                double y0 = Math.min(a.getY(), b.getY());
                double z0 = Math.min(a.getZ(), b.getZ());
                double x1 = Math.max(a.getX(), b.getX()) + 1;
                double y1 = Math.max(a.getY(), b.getY()) + 1;
                double z1 = Math.max(a.getZ(), b.getZ()) + 1;

                BoxRenderer.drawFilledBox(ps, x0, y0, z0, x1, y1, z1, TR, TG, TB, 0.3f);
                LevelRenderer.renderLineBox(ps, linesBuf, x0, y0, z0, x1, y1, z1, TR, TG, TB, TA);

                hudText = dims(a, b);
                bufSource.endBatch(RenderType.lines());
            }
        } else {
            hudText = "";
        }
    }

    // ── CABLES ───────────────────────────────────────────────────────────────────

    private static void renderCableMode(PoseStack ps, Minecraft mc, MMConfig cfg) {
        // The cable line itself is shown as ghost-block hints; draw the selection box around it too.
        if (cfg.coordA != null && cfg.coordB != null) {
            BlockPos a = cfg.coordA.pos;
            BlockPos b = pinToAxis(a, cfg.coordB.pos);

            var bufSource = mc.renderBuffers().bufferSource();
            var linesBuf = bufSource.getBuffer(RenderType.lines());

            double x0 = Math.min(a.getX(), b.getX()),
                    y0 = Math.min(a.getY(), b.getY()),
                    z0 = Math.min(a.getZ(), b.getZ());
            double x1 = Math.max(a.getX(), b.getX()) + 1,
                    y1 = Math.max(a.getY(), b.getY()) + 1,
                    z1 = Math.max(a.getZ(), b.getZ()) + 1;

            BoxRenderer.drawFilledBox(ps, x0, y0, z0, x1, y1, z1, TR, TG, TB, 0.3f);
            LevelRenderer.renderLineBox(ps, linesBuf, x0, y0, z0, x1, y1, z1, TR, TG, TB, TA);
            bufSource.endBatch(RenderType.lines());

            int len = Math.max(
                    Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
                    Math.abs(a.getZ() - b.getZ()))
                    + 1;
            hudText = len + " blocks";
        } else {
            hudText = "";
        }
    }

    // ── COPYING / MOVING ─────────────────────────────────────────────────────────

    private static void renderCopyMode(PoseStack ps, Minecraft mc, MMConfig cfg) {
        // Ghost-block previews at the paste destination are drawn by GhostBlockRenderer
        var bufSource = mc.renderBuffers().bufferSource();
        var linesBuf = bufSource.getBuffer(RenderType.lines());

        if (cfg.coordA != null && cfg.coordB != null) {
            BlockPos a = cfg.coordA.pos, b = cfg.coordB.pos;
            double x0 = Math.min(a.getX(), b.getX());
            double y0 = Math.min(a.getY(), b.getY());
            double z0 = Math.min(a.getZ(), b.getZ());
            double x1 = Math.max(a.getX(), b.getX()) + 1;
            double y1 = Math.max(a.getY(), b.getY()) + 1;
            double z1 = Math.max(a.getZ(), b.getZ()) + 1;

            // Source box (teal)
            BoxRenderer.drawFilledBox(ps, x0, y0, z0, x1, y1, z1, TR, TG, TB, 0.3f);
            LevelRenderer.renderLineBox(ps, linesBuf, x0, y0, z0, x1, y1, z1, TR, TG, TB, TA);
            hudText = dims(a, b);

            // Paste box (orange) — covers the FULL copied region (not just occupied cells), with
            // transform + arraySpan stacking, placed at coordC.
            if (cfg.coordC != null) {
                BlockPos c = cfg.coordC.pos;
                int[] bb = fullPasteBounds(cfg);

                double px0 = c.getX() + bb[0], py0 = c.getY() + bb[1], pz0 = c.getZ() + bb[2];
                double px1 = c.getX() + bb[3] + 1, py1 = c.getY() + bb[4] + 1, pz1 = c.getZ() + bb[5] + 1;

                BoxRenderer.drawFilledBox(ps, px0, py0, pz0, px1, py1, pz1, OR, OG, OB, 0.3f);
                LevelRenderer.renderLineBox(ps, linesBuf, px0, py0, pz0, px1, py1, pz1, OR, OG, OB, TA);
            }
        } else {
            hudText = "";
        }

        bufSource.endBatch(RenderType.lines());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static MMConfig getEffectiveConfig(MMConfig cfg, Minecraft mc) {
        if (cfg.locked || cfg.action == null) return cfg;

        BlockPos look = getLookingAt(mc, cfg.action);
        if (look == null) return cfg;

        var level = mc.level;
        if (level == null) return cfg;
        Location loc = new Location(level.dimension(), look);

        MMConfig eff = cfg.copy();
        switch (cfg.action) {
            case SELECTING_CORNER_B -> eff.coordB = loc;
            case MOVING_COORDS -> {
                // Coords with offsets track the crosshair until committed
                if (cfg.coordAOffset != null) eff.coordA = new Location(loc.dimension, cfg.getCoordA(level, look));
                if (cfg.coordBOffset != null) eff.coordB = new Location(loc.dimension, cfg.getCoordB(level, look));
                if (cfg.coordCOffset != null) eff.coordC = new Location(loc.dimension, cfg.getCoordC(level, look));
            }
            case MARK_ARRAY -> {
                // Live array span preview while aiming
                if (cfg.coordA != null && cfg.coordB != null && cfg.coordC != null) {
                    var mult = cfg.getArrayMult(look);
                    eff.arraySpan = new int[] { mult.x, mult.y, mult.z };
                }
            }
            case MARK_COPY_A, MARK_CUT_A -> {
                eff.coordA = loc;
                eff.coordB = null;
            }
            case MARK_COPY_B, MARK_CUT_B -> eff.coordB = loc;
            case MARK_PASTE_A -> eff.coordC = loc;
            default -> {}
        }
        return eff;
    }

    // ── Targeted-block ruler ───────────────────────────────────────────────────────

    private static final int RULER_LENGTH = 128;

    /** Draws axis lines through the block currently being targeted by an active action, for alignment. */
    private static void renderTargetRuler(PoseStack ps, Minecraft mc, MMConfig cfg, @Nullable PendingAction action) {
        if (cfg.locked || action == null) return;
        BlockPos look = getLookingAt(mc, action);
        if (look == null) return;

        // Cable placement pins the endpoint to one axis from coordA; put the ruler's location there
        // too (the lines themselves still span all three axes).
        if (cfg.placeMode == PlaceMode.CABLES && cfg.coordA != null) {
            look = pinToAxis(cfg.coordA.pos, look);
        }

        float r, g, b;
        switch (action) {
            case MARK_PASTE_A -> {
                r = OR;
                g = OG;
                b = OB;
            }
            case MARK_ARRAY -> {
                r = 0.4f;
                g = 0.75f;
                b = 0.15f;
            }
            default -> {
                r = TR;
                g = TG;
                b = TB;
            }
        }

        // Drawn via the standard RenderType.lines() through the level's buffer source — the same path
        // the boxes use, which renders correct thin ruler lines.
        var bufSource = mc.renderBuffers().bufferSource();
        var linesBuf = bufSource.getBuffer(RenderType.lines());
        drawRulers(ps, linesBuf, look.getX() + 0.5, look.getY() + 0.5, look.getZ() + 0.5, r, g, b, TA, RULER_LENGTH);
        bufSource.endBatch(RenderType.lines());
    }

    /**
     * Draws axis-aligned ruler lines extending from (cx, cy, cz) in all 6 directions.
     * {@code linesBuf} must come from a {@code RenderType.lines()} buffer.
     */
    public static void drawRulers(
            PoseStack ps,
            VertexConsumer linesBuf,
            double cx,
            double cy,
            double cz,
            float r,
            float g,
            float b,
            float a,
            int length) {
        float fx = (float) cx, fy = (float) cy, fz = (float) cz;
        var pose = ps.last();

        addLine(linesBuf, pose, fx, fy, fz, fx + length, fy, fz, r, g, b, a, 1, 0, 0);
        addLine(linesBuf, pose, fx, fy, fz, fx - length, fy, fz, r, g, b, a, -1, 0, 0);
        addLine(linesBuf, pose, fx, fy, fz, fx, fy + length, fz, r, g, b, a, 0, 1, 0);
        addLine(linesBuf, pose, fx, fy, fz, fx, fy - length, fz, r, g, b, a, 0, -1, 0);
        addLine(linesBuf, pose, fx, fy, fz, fx, fy, fz + length, r, g, b, a, 0, 0, 1);
        addLine(linesBuf, pose, fx, fy, fz, fx, fy, fz - length, r, g, b, a, 0, 0, -1);
    }

    private static void addLine(
            VertexConsumer buf,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float r,
            float g,
            float b,
            float a,
            float nx,
            float ny,
            float nz) {
        buf.vertex(pose.pose(), x1, y1, z1)
                .color(r, g, b, a)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        buf.vertex(pose.pose(), x2, y2, z2)
                .color(r, g, b, a)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
    }

    /** Pins {@code b} to the dominant axis relative to {@code a}. */
    private static BlockPos pinToAxis(BlockPos a, BlockPos b) {
        int dx = Math.abs(b.getX() - a.getX());
        int dy = Math.abs(b.getY() - a.getY());
        int dz = Math.abs(b.getZ() - a.getZ());
        if (dx >= dy && dx >= dz) return new BlockPos(b.getX(), a.getY(), a.getZ());
        if (dy >= dx && dy >= dz) return new BlockPos(a.getX(), b.getY(), a.getZ());
        return new BlockPos(a.getX(), a.getY(), b.getZ());
    }

    @Nullable
    public static BlockPos getLookingAt(Minecraft mc, @Nullable PendingAction action) {
        var hit = mc.hitResult;
        var player = mc.player;
        if (player == null) return null;

        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            var bhr = (BlockHitResult) hit;
            if (player.isShiftKeyDown() || (action != null && action.isDirectBlockPick())) {
                return bhr.getBlockPos();
            }
            return bhr.getBlockPos().relative(bhr.getDirection());
        }

        // Not looking at a block (air/entity)
        if (action != null && action.isDirectBlockPick()) return null;

        return player.blockPosition();
    }

    private static List<PendingBlock> analysisCache = Collections.emptyList();
    private static long lastAnalysisTime = 0;
    private static MMConfig lastAnalyzedConfig = null;
    private static long analysisSignature = 0;

    private static void updateAnalysisCache(MMConfig cfg, ClientLevel level) {
        if (level == null) return;
        long now = System.currentTimeMillis();
        boolean analysisSame = lastAnalyzedConfig != null && analysisInputsEqual(lastAnalyzedConfig, cfg);

        // Moving the paste anchor (coordC) leaves the analysis unchanged; skip regenerating but keep
        // the baseline current (incl. the new coordC) so the next comparison stays stable. The new
        // coordC still flows to GhostBlockRenderer as the render origin and re-bakes there.
        if (analysisSame && now - lastAnalysisTime < 1000) {
            lastAnalyzedConfig = cfg.copy();
            return;
        }

        lastAnalysisTime = now;
        lastAnalyzedConfig = cfg.copy();

        var fresh = BuildGenerator.generateBlocks(cfg, level);
        long sig = signatureOf(fresh);
        // The 1s refresh exists to catch world edits, but re-baking thousands of block models is
        // expensive. When the inputs are unchanged and the generated blocks are identical, keep the
        // old list reference so GhostBlockRenderer's reference guard skips the rebake.
        if (analysisSame && sig == analysisSignature && !analysisCache.isEmpty()) return;
        analysisCache = fresh;
        analysisSignature = sig;
    }

    /**
     * Whether two configs produce the same {@link BuildGenerator#generateBlocks} output. For
     * COPYING/MOVING the paste anchor (coordC) is applied downstream as a render origin and does not
     * affect the generated (source-relative) blocks, so its value is ignored — only whether it is set
     * matters (a null coordC yields an empty build). All other modes compare normally (e.g. cylinder
     * geometry genuinely uses coordC).
     */
    private static boolean analysisInputsEqual(MMConfig a, MMConfig b) {
        if (a.placeMode != b.placeMode) return false;
        if (a.placeMode == PlaceMode.COPYING || a.placeMode == PlaceMode.MOVING) {
            if ((a.coordC == null) != (b.coordC == null)) return false;
            MMConfig norm = a.copy();
            norm.coordC = b.coordC;
            return norm.equals(b);
        }
        return a.equals(b);
    }

    /**
     * Bounding box (coordC-relative, max inclusive) of the FULL copied region for the paste preview:
     * the whole coordA→coordB volume (regardless of which cells hold blocks), with the transform and
     * arraySpan stacking applied. Returned as {minX,minY,minZ,maxX,maxY,maxZ}.
     *
     * <p>The copy maps a source cell to {@code coordC + transform(relPos + arrayOffset)} where
     * {@code relPos = world - coordA}. Since the transform only permutes/flips axes, transforming the
     * two opposite corners of the raw (relPos + arrayOffset) box yields the result box.
     */
    private static int[] fullPasteBounds(MMConfig cfg) {
        BlockPos a = cfg.coordA.pos, b = cfg.coordB.pos;
        boolean moving = cfg.placeMode == PlaceMode.MOVING;
        int[] span = cfg.arraySpan;
        int sx = !moving && span != null ? span[0] : 0;
        int sy = !moving && span != null ? span[1] : 0;
        int sz = !moving && span != null ? span[2] : 0;

        long[] rx = axisRange(b.getX() - a.getX(), sx);
        long[] ry = axisRange(b.getY() - a.getY(), sy);
        long[] rz = axisRange(b.getZ() - a.getZ(), sz);

        Vector3i c0 = cfg.transform.apply(new Vector3i((int) rx[0], (int) ry[0], (int) rz[0]));
        Vector3i c1 = cfg.transform.apply(new Vector3i((int) rx[1], (int) ry[1], (int) rz[1]));
        return new int[] {
                Math.min(c0.x(), c1.x()), Math.min(c0.y(), c1.y()), Math.min(c0.z(), c1.z()),
                Math.max(c0.x(), c1.x()), Math.max(c0.y(), c1.y()), Math.max(c0.z(), c1.z()),
        };
    }

    /** Combined min/max along one axis of (relPos + arrayOffset) for a signed region delta and span. */
    private static long[] axisRange(int delta, int span) {
        long relMin = Math.min(0, delta), relMax = Math.max(0, delta);
        int step = delta + (delta < 0 ? -1 : 1); // copy spacing = region size, signed
        long o0 = (long) Math.min(span, 0) * step, o1 = (long) Math.max(span, 0) * step;
        return new long[] { relMin + Math.min(o0, o1), relMax + Math.max(o0, o1) };
    }

    /** Cheap order-sensitive hash over positions + states; far cheaper than re-baking models. */
    private static long signatureOf(List<PendingBlock> blocks) {
        long h = 1;
        for (var pb : blocks) {
            h = h * 31 + pb.relPos.asLong();
            h = h * 31 + pb.spec.state.hashCode();
        }
        return h;
    }

    private static String dims(BlockPos a, BlockPos b) {
        return (Math.abs(a.getX() - b.getX()) + 1) + "x"
                + (Math.abs(a.getY() - b.getY()) + 1) + "x"
                + (Math.abs(a.getZ() - b.getZ()) + 1);
    }
}
