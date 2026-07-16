package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.building.analysis.ITileAnalysis;
import dev.wp.matter_manipulator.common.building.shapes.ShapeBlock;
import dev.wp.matter_manipulator.common.building.shapes.Shapes;
import dev.wp.matter_manipulator.common.compat.CompatRegistry;
import dev.wp.matter_manipulator.common.compat.ExtendedAECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.PlaceMode;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

public class BuildGenerator {
    public static List<PendingBlock> generateBlocks(MMConfig cfg, Level level) {
        return generateBlocks(cfg, level, null);
    }

    /**
     * @param builder the player whose build this is, or null for the client preview / required-items
     *                pass. Only used by MOVING to skip protected source blocks at capture (server build only).
     */
    public static List<PendingBlock> generateBlocks(MMConfig cfg, Level level, @Nullable Player builder) {
        return switch (cfg.placeMode) {
            case GEOMETRY -> generateGeometry(cfg, level);
            case EXCHANGING -> generateExchanging(cfg, level);
            case COPYING, MOVING -> generateCopyMove(cfg, level, cfg.placeMode == PlaceMode.MOVING, builder);
            case CABLES -> generateCables(cfg, level);
            default -> Collections.emptyList();
        };
    }

    private static List<PendingBlock> generateGeometry(MMConfig cfg, Level level) {
        if (cfg.coordA == null || cfg.coordB == null) return Collections.emptyList();

        List<ShapeBlock> shapeBlocks = switch (cfg.shape) {
            case LINE -> Shapes.line(cfg.coordA.pos, cfg.coordB.pos);
            case CUBE -> Shapes.cube(cfg.coordA.pos, cfg.coordB.pos);
            case SPHERE -> Shapes.sphere(cfg.coordA.pos, cfg.coordB.pos);
            case CYLINDER -> {
                if (cfg.coordC == null) yield Collections.emptyList();
                yield Shapes.cylinder(cfg.coordA.pos, cfg.coordB.pos, cfg.coordC.pos);
            }
        };

        var rng = level.getRandom();
        var blocks = new ArrayList<PendingBlock>();
        for (var sb : shapeBlocks) {
            var palette = switch (sb.slot()) {
                case CORNER -> cfg.corners;
                case EDGE -> cfg.edges;
                case FACE -> cfg.faces;
                case VOLUME -> cfg.volumes;
                case ALL -> throw new AssertionError();
            };
            var picked = palette.pick(rng);
            // Geometry places fresh blocks: the palette spec is state-only, no analyses attached.
            if (picked != null) {
                blocks.add(new PendingBlock(sb.pos(), picked));
            }
        }
        return blocks;
    }

    private static List<PendingBlock> generateExchanging(MMConfig cfg, Level level) {
        if (cfg.coordA == null || cfg.coordB == null) return Collections.emptyList();

        var a = cfg.coordA.pos;
        var b = cfg.coordB.pos;
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());

        var rng = level.getRandom();
        var blocks = new ArrayList<PendingBlock>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    var pos = new BlockPos(x, y, z);
                    var current = level.getBlockState(pos);
                    if (cfg.replaceWhitelist.contains(current.getBlock())) {
                        var picked = cfg.replaceWith.pick(rng);
                        if (picked == null) continue;

                        // Carry over every shared blockstate property (facing/orientation/variant, GT trio); for a
                        // type change, capture old tile state so covers/inventory/fluids migrate via COPYING analyses.
                        BlockState placed = picked.state;
                        for (Property<?> prop : current.getProperties()) {
                            placed = copyProperty(placed, current, prop);
                        }
                        var pb = new PendingBlock(pos, new BlockSpec(placed));
                        if (placed.getBlock() != current.getBlock()) {
                            pb.analyze(level, pos, false);
                        }
                        blocks.add(pb);
                    }
                }
            }
        }
        return blocks;
    }

    /** Sets {@code prop} on {@code target} to its value in {@code source}, if the target has it and the value is valid. */
    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState target, BlockState source, Property<T> prop) {
        if (!target.hasProperty(prop)) return target;
        T value = source.getValue(prop);
        if (!prop.getPossibleValues().contains(value)) return target;
        return target.setValue(prop, value);
    }

    private static List<PendingBlock> generateCopyMove(
            MMConfig cfg, Level level, boolean moving, @Nullable Player builder) {
        if (cfg.coordA == null || cfg.coordB == null || cfg.coordC == null) return Collections.emptyList();

        var rawBlocks = BlockAnalyzer.analyzeRegion(level, cfg.coordA.pos, cfg.coordB.pos, moving, builder);
        if (rawBlocks.isEmpty()) return Collections.emptyList();

        boolean identity = cfg.transform.isIdentity();

        // Wireless smart-copy region bounds (ExtendedAE), used to classify links as internal/external.
        boolean wireless = Mods.AE2 && Mods.EXTENDED_AE && !moving;
        BlockPos regionMin = null, regionMax = null;
        if (wireless) {
            var ca = cfg.coordA.pos;
            var cb = cfg.coordB.pos;
            regionMin = new BlockPos(
                    Math.min(ca.getX(), cb.getX()), Math.min(ca.getY(), cb.getY()), Math.min(ca.getZ(), cb.getZ()));
            regionMax = new BlockPos(
                    Math.max(ca.getX(), cb.getX()), Math.max(ca.getY(), cb.getY()), Math.max(ca.getZ(), cb.getZ()));
        }

        var baseBlocks = new ArrayList<PendingBlock>();
        for (var pb : rawBlocks) {
            Vector3i transformedRel = cfg.transform.apply(new Vector3i(pb.relPos.getX(), pb.relPos.getY(), pb.relPos.getZ()));

            BlockState original = pb.spec.state;
            BlockState blockState = original;

            // 1. Vanilla rotation/mirror: rotates shape/axis (logs, stairs) and horizontal facing.
            var rm = cfg.transform.deriveRotationMirror();
            if (rm.mirror() != Mirror.NONE) blockState = blockState.mirror(rm.mirror());
            if (rm.rotation() != Rotation.NONE) blockState = blockState.rotate(level, pb.relPos, rm.rotation());

            // 2. Override each facing property with the full transform (handles vertical facing).
            for (Property<?> prop : original.getProperties()) {
                if (prop.getValueClass() == Direction.class) {
                    @SuppressWarnings("unchecked")
                    Property<Direction> dirProp = (Property<Direction>) prop;
                    Direction rotated = cfg.transform.apply(original.getValue(dirProp));
                    if (dirProp.getPossibleValues().contains(rotated)) {
                        blockState = blockState.setValue(dirProp, rotated);
                    }
                }
            }

            // 3. GregTech extended facing (upwards facing is a relative roll the generic pass can't express).
            if (Mods.GTCEU && !identity) {
                blockState = GTCompat.transformBlockState(blockState, original, cfg.transform);
            }

            // Smart copy (COPYING): a captured GTCEu ME Pattern Buffer (CRIB) becomes a Pattern Buffer Proxy
            // linked to the original's world position (it stays in place), replacing its analyses with the link.
            BlockPos cribMaster = null;
            if (Mods.GTCEU && !moving && cfg.replaceCribsWithProxies) {
                BlockPos worldPos = cfg.coordA.pos.offset(pb.relPos.getX(), pb.relPos.getY(), pb.relPos.getZ());
                if (GTCompat.isPatternBuffer(level.getBlockEntity(worldPos))) {
                    cribMaster = worldPos;
                    blockState = GTCompat.proxyState(blockState);
                }
            }

            // Wireless smart-copy: a hub with no links inside the copy area becomes a connector tethered
            // to the original hub (which keeps free ports for each subsequent paste).
            BlockPos hubTether = null;
            if (wireless && cribMaster == null && cfg.tetherEmptyHubs) {
                BlockPos worldPos = cfg.coordA.pos.offset(pb.relPos.getX(), pb.relPos.getY(), pb.relPos.getZ());
                if (ExtendedAECompat.isTetherableHub(level.getBlockEntity(worldPos), regionMin, regionMax)) {
                    hubTether = worldPos;
                    blockState = ExtendedAECompat.connectorState();
                }
            }

            // 4. Rotate the analyses' orientation (cover sides, AE part sides, isFlipped).
            var out = new PendingBlock(
                    new BlockPos(transformedRel.x(), transformedRel.y(), transformedRel.z()),
                    new BlockSpec(blockState),
                    pb.buildOrder);
            if (cribMaster != null) {
                out.addAnalysis(new GTCompat.PatternBufferProxyAnalysis(cribMaster));
            } else if (hubTether != null) {
                out.addAnalysis(ExtendedAECompat.tetherToHub(hubTether));
            } else {
                for (var analysis : pb.analyses) out.analyses.add(analysis.copy());
                if (!identity) out.transform(cfg.transform);
                // Wireless smart-copy: capture a connector/hub's links so they re-form on paste.
                if (wireless) {
                    BlockPos worldPos = cfg.coordA.pos.offset(
                            pb.relPos.getX(), pb.relPos.getY(), pb.relPos.getZ());
                    var wbe = level.getBlockEntity(worldPos);
                    if (ExtendedAECompat.isWireless(wbe)) {
                        var wa = ExtendedAECompat.captureWireless(wbe, regionMin, regionMax, cfg.linkExternalHubs);
                        if (wa != null) out.addAnalysis(wa);
                    }
                }
            }
            baseBlocks.add(out);
        }

        var blocks = new ArrayList<PendingBlock>();
        if (!moving && (cfg.arraySpan[0] != 0 || cfg.arraySpan[1] != 0 || cfg.arraySpan[2] != 0)) {
            int sx = cfg.arraySpan[0], sy = cfg.arraySpan[1], sz = cfg.arraySpan[2];
            int dx = cfg.coordB.pos.getX() - cfg.coordA.pos.getX();
            int dy = cfg.coordB.pos.getY() - cfg.coordA.pos.getY();
            int dz = cfg.coordB.pos.getZ() - cfg.coordA.pos.getZ();

            for (int y = Math.min(sy, 0); y <= Math.max(sy, 0); y++) {
                for (int z = Math.min(sz, 0); z <= Math.max(sz, 0); z++) {
                    for (int x = Math.min(sx, 0); x <= Math.max(sx, 0); x++) {
                        int offsetX = x * (dx + (dx < 0 ? -1 : 1));
                        int offsetY = y * (dy + (dy < 0 ? -1 : 1));
                        int offsetZ = z * (dz + (dz < 0 ? -1 : 1));

                        Vector3i offset = cfg.transform.apply(new Vector3i(offsetX, offsetY, offsetZ));
                        for (var pb : baseBlocks) {
                            blocks.add(pb.at(pb.relPos.offset(offset.x, offset.y, offset.z)));
                        }
                    }
                }
            }
        } else {
            blocks.addAll(baseBlocks);
        }

        return blocks;
    }

    private static List<PendingBlock> generateCables(MMConfig cfg, Level level) {
        if (cfg.coordA == null || cfg.coordB == null || cfg.cables.isAir()) return Collections.emptyList();

        var a = cfg.coordA.pos;
        var b = cfg.coordB.pos;
        Vector3i pinnedB = MMConfig.pinToAxes(
                new Vector3i(a.getX(), a.getY(), a.getZ()), new Vector3i(b.getX(), b.getY(), b.getZ()));

        var line = Shapes.line(a, new BlockPos(pinnedB.x, pinnedB.y, pinnedB.z));
        var blocks = new ArrayList<PendingBlock>();
        for (var sb : line) {
            var pb = new PendingBlock(sb.pos(), cfg.cables);
            attachCableAnalysis(cfg, pb);
            blocks.add(pb);
        }
        return blocks;
    }

    /**
     * Attaches the minimal cable analysis captured at pick time (AE center cable, or a GT pipe's
     * paint color) so cables-mode placement reproduces the cable's identity/color without copying
     * the rest of the source bus.
     */
    private static void attachCableAnalysis(MMConfig cfg, PendingBlock pb) {
        if (cfg.cableTile == null) return;
        CompoundTag nbt;
        try {
            nbt = TagParser.parseTag(cfg.cableTile);
        } catch (Exception e) {
            return;
        }
        String id = nbt.getString("id");
        ITileAnalysis analysis = CompatRegistry.reconstructCable(id, nbt);
        if (analysis != null) pb.addAnalysis(analysis);
    }
}
