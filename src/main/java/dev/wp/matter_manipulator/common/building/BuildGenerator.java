package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.building.shapes.ShapeBlock;
import dev.wp.matter_manipulator.common.building.shapes.ShapeCube;
import dev.wp.matter_manipulator.common.building.shapes.ShapeCylinder;
import dev.wp.matter_manipulator.common.building.shapes.ShapeLine;
import dev.wp.matter_manipulator.common.building.shapes.ShapeSphere;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BuildGenerator {

    public static List<PendingBlock> generateBlocks(MMConfig cfg, Level level) {
        return switch (cfg.placeMode) {
            case GEOMETRY   -> generateGeometry(cfg, level);
            case EXCHANGING -> generateExchanging(cfg, level);
            case COPYING, MOVING -> generateCopyMove(cfg, level, cfg.placeMode == PlaceMode.MOVING);
            case CABLES     -> generateCables(cfg, level);
            default -> Collections.emptyList();
        };
    }

    private static List<PendingBlock> generateGeometry(MMConfig cfg, Level level) {
        if (cfg.coordA == null || cfg.coordB == null) return Collections.emptyList();
        
        List<ShapeBlock> shapeBlocks = switch (cfg.shape) {
            case LINE     -> ShapeLine.generate(cfg.coordA.pos, cfg.coordB.pos);
            case CUBE     -> ShapeCube.generate(cfg.coordA.pos, cfg.coordB.pos);
            case SPHERE   -> ShapeSphere.generate(cfg.coordA.pos, cfg.coordB.pos);
            case CYLINDER -> {
                if (cfg.coordC == null) yield Collections.emptyList();
                yield ShapeCylinder.generate(cfg.coordA.pos, cfg.coordB.pos, cfg.coordC.pos);
            }
        };

        var rng = level.getRandom();
        var blocks = new ArrayList<PendingBlock>();
        for (var sb : shapeBlocks) {
            var palette = switch (sb.slot()) {
                case CORNER -> cfg.corners;
                case EDGE   -> cfg.edges;
                case FACE   -> cfg.faces;
                case VOLUME -> cfg.volumes;
            };
            var picked = palette.pick(rng);
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
                        if (picked != null) {
                            blocks.add(new PendingBlock(pos, picked));
                        }
                    }
                }
            }
        }
        return blocks;
    }

    private static List<PendingBlock> generateCopyMove(MMConfig cfg, Level level, boolean moving) {
        if (cfg.coordA == null || cfg.coordB == null || cfg.coordC == null) return Collections.emptyList();

        var rawBlocks = BlockAnalyzer.analyzeRegion(level, cfg.coordA.pos, cfg.coordB.pos);
        if (rawBlocks.isEmpty()) return Collections.emptyList();

        var baseBlocks = new ArrayList<PendingBlock>();
        for (var pb : rawBlocks) {
            Vector3i transformedRel = cfg.transform.apply(new Vector3i(pb.relPos.getX(), pb.relPos.getY(), pb.relPos.getZ()));
            
            BlockState blockState = pb.spec.state;
            int rotationY = switch (cfg.transform.forward) {
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> 0;
            };
            Rotation rotation = switch (rotationY) {
                case 1 -> Rotation.CLOCKWISE_90;
                case 2 -> Rotation.CLOCKWISE_180;
                case 3 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            if (rotation != Rotation.NONE) blockState = blockState.rotate(level, BlockPos.ZERO, rotation);
            if (cfg.transform.flipX) blockState = blockState.mirror(Mirror.LEFT_RIGHT);
            if (cfg.transform.flipZ) blockState = blockState.mirror(Mirror.FRONT_BACK);

            baseBlocks.add(new PendingBlock(new BlockPos(transformedRel.x(), transformedRel.y(), transformedRel.z()), new BlockSpec(blockState, pb.spec.tileData, pb.spec.contents)));
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
                            blocks.add(new PendingBlock(pb.relPos.offset(offset.x, offset.y, offset.z), pb.spec));
                        }
                    }
                }
            }
        } else {
            blocks.addAll(baseBlocks);
        }

        // Relativize to Paste Origin (coordC) for PendingBuild
        // Wait, PacketStartBuild was using BlockPos.ZERO as anchor and pb.relPos as relative to it.
        // So the blocks already have the correct relative positions.
        return blocks;
    }

    private static List<PendingBlock> generateCables(MMConfig cfg, Level level) {
        if (cfg.coordA == null || cfg.coordB == null || cfg.cables.isAir()) return Collections.emptyList();
        
        var a = cfg.coordA.pos;
        var b = cfg.coordB.pos;
        Vector3i pinnedB = MMConfig.pinToAxes(new Vector3i(a.getX(), a.getY(), a.getZ()), new Vector3i(b.getX(), b.getY(), b.getZ()));

        var line = ShapeLine.generate(a, new BlockPos(pinnedB.x, pinnedB.y, pinnedB.z));
        var blocks = new ArrayList<PendingBlock>();
        for (var sb : line) {
            blocks.add(new PendingBlock(sb.pos(), cfg.cables));
        }
        return blocks;
    }
}
