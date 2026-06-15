package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Server-side block-selection actions, shared by the pending-action right-click path
 * (PacketSetCoord) and middle-mouse picking (PacketMMBPressed).
 */
public class ManipulatorActions {
    /**
     * Sets or adds (when {@code add}) a block in the palette list(s) chosen by
     * {@code selectMode}.
     */
    public static void pickBlock(
            ServerPlayer player, MMConfig cfg, BlockSelectMode selectMode, BlockPos pos, boolean add) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);

        String what = switch (selectMode) {
            case CORNERS -> "corners";
            case EDGES -> "edges";
            case FACES -> "faces";
            case VOLUMES -> "volumes";
            case ALL -> "all blocks";
        };

        switch (selectMode) {
            case CORNERS -> {
                if (!add) cfg.corners = new WeightedSpecList();
                cfg.corners.add(block, 1);
            }
            case EDGES -> {
                if (!add) cfg.edges = new WeightedSpecList();
                cfg.edges.add(block, 1);
            }
            case FACES -> {
                if (!add) cfg.faces = new WeightedSpecList();
                cfg.faces.add(block, 1);
            }
            case VOLUMES -> {
                if (!add) cfg.volumes = new WeightedSpecList();
                cfg.volumes.add(block, 1);
            }
            case ALL -> {
                if (!add) {
                    cfg.corners = new WeightedSpecList();
                    cfg.edges = new WeightedSpecList();
                    cfg.faces = new WeightedSpecList();
                    cfg.volumes = new WeightedSpecList();
                }
                cfg.corners.add(block, 1);
                cfg.edges.add(block, 1);
                cfg.faces.add(block, 1);
                cfg.volumes.add(block, 1);
            }
        }

        if (add) {
            MMUtils.sendInfoToPlayer(player, "mm.info.added", displayName(block), what);
        } else {
            MMUtils.sendInfoToPlayer(player, "mm.info.set", what, displayName(block));
        }
    }

    /** Air is valid — "replace with air" means delete matching blocks. */
    public static void exchangeSetTarget(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        cfg.replaceWith = new WeightedSpecList();
        cfg.replaceWith.add(block, 1);
        MMUtils.sendInfoToPlayer(player, "mm.info.set_block_to_replace_with", displayName(block));
    }

    public static void exchangeAddWhitelist(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        if (block.isAir()) return;
        if (cfg.replaceWhitelist == null) cfg.replaceWhitelist = new WeightedSpecList();
        cfg.replaceWhitelist.add(block, 1);
        MMUtils.sendInfoToPlayer(player, "mm.info.added_block_to_exchange_whitelist", displayName(block));
    }

    public static void exchangeSetWhitelist(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        if (block.isAir()) return;
        cfg.replaceWhitelist = new WeightedSpecList();
        cfg.replaceWhitelist.add(block, 1);
        MMUtils.sendInfoToPlayer(player, "mm.info.set_exchange_whitelist_to_only_contain", displayName(block));
    }

    /** Picking air clears the cable block. */
    public static void pickCable(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockSpec block = BlockSpec.fromWorld(level, pos);
        cfg.cables = block;
        cfg.cableTile = captureCableTile(level, pos);
        if (block.isAir()) {
            MMUtils.sendInfoToPlayer(player, "mm.info.cleared_cable");
        } else {
            MMUtils.sendInfoToPlayer(player, "mm.info.set_cable", displayName(block));
        }
    }

    /**
     * Captures only the cable's identity/color for cables mode: an AE2 center cable, or a GT pipe's
     * paint color, as SNBT. Null for plain blocks. This is the faithful "lightweight capture at pick
     * time" — the rest of the source bus is intentionally dropped.
     */
    @Nullable
    private static String captureCableTile(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        CompoundTag nbt = null;
        if (Mods.AE2 && AECompat.isAEBlock(be)) {
            AECompat.AEAnalysis analysis = AECompat.AEAnalysis.analyze(be);
            AECompat.AEAnalysis cable = analysis == null ? null : analysis.cableOnly();
            if (cable != null) nbt = cable.toNbt();
        } else if (Mods.GTCEU) {
            GTCompat.GTAnalysis analysis = GTCompat.GTAnalysis.analyze(be);
            GTCompat.GTAnalysis color = analysis == null ? null : analysis.colorOnly();
            if (color != null) nbt = color.toNbt();
        }
        return nbt == null ? null : nbt.toString();
    }

    private static Component displayName(BlockSpec block) {
        return block.getDisplayName();
    }
}
