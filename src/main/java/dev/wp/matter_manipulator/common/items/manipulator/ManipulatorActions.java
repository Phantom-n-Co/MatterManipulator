package dev.wp.matter_manipulator.common.items.manipulator;

import dev.wp.matter_manipulator.common.building.BlockSpec;
import dev.wp.matter_manipulator.common.compat.CompatRegistry;
import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

import static dev.wp.matter_manipulator.MMMod.MODID;

/**
 * Server-side block-selection actions, shared by the pending-action right-click path
 * (PacketSetCoord) and middle-mouse picking (PacketMMBPressed).
 */
public class ManipulatorActions {
    public static void pickBlock(
            ServerPlayer player, MMConfig cfg, SlotType selectMode, BlockPos pos, boolean add) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);

        String what = selectMode == SlotType.ALL ? "all blocks" : selectMode.name().toLowerCase() + "s";

        for (SlotType t : selectMode == SlotType.ALL
                ? new SlotType[] { SlotType.CORNER, SlotType.EDGE, SlotType.FACE, SlotType.VOLUME }
                : new SlotType[] { selectMode }) {
            var list = switch (t) {
                case CORNER -> {
                    if (!add) cfg.corners = new WeightedSpecList();
                    yield cfg.corners;
                }
                case EDGE -> {
                    if (!add) cfg.edges = new WeightedSpecList();
                    yield cfg.edges;
                }
                case FACE -> {
                    if (!add) cfg.faces = new WeightedSpecList();
                    yield cfg.faces;
                }
                case VOLUME -> {
                    if (!add) cfg.volumes = new WeightedSpecList();
                    yield cfg.volumes;
                }
                default -> throw new AssertionError();
            };
            list.add(block, 1);
        }

        if (add) {
            MMUtils.sendInfoToPlayer(player, MODID + ".info.added", displayName(block), what);
        } else {
            MMUtils.sendInfoToPlayer(player, MODID + ".info.set", what, displayName(block));
        }
    }

    /** Air is valid — "replace with air" means delete matching blocks. */
    public static void exchangeSetTarget(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        cfg.replaceWith = new WeightedSpecList();
        cfg.replaceWith.add(block, 1);
        MMUtils.sendInfoToPlayer(player, MODID + ".info.set_block_to_replace_with", displayName(block));
    }

    public static void exchangeAddWhitelist(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        if (block.isAir()) return;
        if (cfg.replaceWhitelist == null) cfg.replaceWhitelist = new WeightedSpecList();
        cfg.replaceWhitelist.add(block, 1);
        MMUtils.sendInfoToPlayer(player, MODID + ".info.added_block_to_exchange_whitelist", displayName(block));
    }

    public static void exchangeSetWhitelist(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        BlockSpec block = BlockSpec.fromWorld(player.serverLevel(), pos);
        if (block.isAir()) return;
        cfg.replaceWhitelist = new WeightedSpecList();
        cfg.replaceWhitelist.add(block, 1);
        MMUtils.sendInfoToPlayer(player, MODID + ".info.set_exchange_whitelist_to_only_contain", displayName(block));
    }

    /** Picking air clears the cable block. */
    public static void pickCable(ServerPlayer player, MMConfig cfg, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockSpec block = BlockSpec.fromWorld(level, pos);
        cfg.cables = block;
        cfg.cableTile = captureCableTile(level, pos);
        if (block.isAir()) {
            MMUtils.sendInfoToPlayer(player, MODID + ".info.cleared_cable");
        } else {
            MMUtils.sendInfoToPlayer(player, MODID + ".info.set_cable", displayName(block));
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
        CompoundTag nbt = CompatRegistry.analyzeCable(be);
        return nbt == null ? null : nbt.toString();
    }

    private static Component displayName(BlockSpec block) {
        return block.getDisplayName();
    }
}
