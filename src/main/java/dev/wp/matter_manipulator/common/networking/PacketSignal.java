package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.PendingBlock;
import dev.wp.matter_manipulator.common.building.PendingBuild;
import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMCapability;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

public class PacketSignal {
    public enum Action {
        CANCEL_BUILD, CLEAR_PLAN, START_BUILD, TOGGLE_BUILD
    }

    public final Action action;

    public PacketSignal(Action action) {
        this.action = action;
    }

    public static void encode(PacketSignal pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
    }

    public static PacketSignal decode(FriendlyByteBuf buf) {
        return new PacketSignal(buf.readEnum(Action.class));
    }

    public static void handle(PacketSignal pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (pkt.action) {
                case CANCEL_BUILD -> {
                    PendingBuild.cancel(player);
                    player.sendSystemMessage(Component.literal("Build cancelled."));
                }
                case CLEAR_PLAN -> {
                    var state = MMState.ofHeld(player.getMainHandItem());
                    if (state == null) return;
                    var cfg = state.getConfig();
                    if (!Mods.AE2 || cfg.uplink == null) {
                        MMUtils.sendErrorToPlayer(player, MODID + ".info.error.no_uplink");
                        return;
                    }
                    int cleared = AECompat.uplinkClearPlan(player.serverLevel(), cfg.uplink, player.getUUID());
                    if (cleared < 0) {
                        MMUtils.sendErrorToPlayer(player, MODID + ".info.error.no_uplink");
                    } else {
                        MMUtils.sendInfoToPlayer(player, MODID + ".info.plan_cleared", cleared);
                    }
                }
                case START_BUILD -> executeStart(player);
                case TOGGLE_BUILD -> {
                    if (PendingBuild.hasActiveBuild(player.getUUID())) {
                        PendingBuild.cancel(player);
                        player.sendSystemMessage(Component.literal("Build cancelled."));
                    } else {
                        executeStart(player);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void executeStart(ServerPlayer player) {
        var held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMatterManipulator item)) return;

        var state = MMState.of(held);
        var cfg = state.getConfig();
        var tier = item.getTier();

        if (cfg.placeMode == PlaceMode.GEOMETRY && !state.hasCapability(tier, MMCapability.ALLOW_GEOMETRY)) return;
        if (cfg.placeMode == PlaceMode.COPYING && !state.hasCapability(tier, MMCapability.ALLOW_COPYING)) return;
        if (cfg.placeMode == PlaceMode.MOVING && !state.hasCapability(tier, MMCapability.ALLOW_MOVING)) return;
        if (cfg.placeMode == PlaceMode.EXCHANGING && !state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING)) return;
        if (cfg.placeMode == PlaceMode.CABLES && !state.hasCapability(tier, MMCapability.ALLOW_CABLES)) return;

        var level = player.serverLevel();
        if (cfg.coordA != null && !cfg.coordA.dimension.equals(level.dimension())) {
            player.sendSystemMessage(Component.literal("Coords must be in the current dimension."));
            return;
        }

        List<PendingBlock> blocks = BuildGenerator.generateBlocks(cfg, level, player);

        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal("No blocks to build. Check selection and palette."));
            return;
        }

        BlockPos anchor = cfg.placeMode.isCopyOrMove() ? cfg.coordC.pos : BlockPos.ZERO;

        BlockPos sourceMin = null, sourceMax = null;
        if (cfg.placeMode == PlaceMode.MOVING) {
            var a = cfg.coordA.pos;
            var b = cfg.coordB.pos;
            sourceMin = new BlockPos(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            sourceMax = new BlockPos(
                    Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }

        PendingBuild.start(player, tier, blocks, anchor, cfg.placeMode == PlaceMode.MOVING, sourceMin, sourceMax, cfg.placeMode == PlaceMode.CABLES);
    }
}
