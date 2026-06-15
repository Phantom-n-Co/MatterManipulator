package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PendingAction;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.items.manipulator.ShapeType;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Vector3i;

/**
 * Radial menu "Move Coords" options. A/B/ALL start tracking the crosshair (committed
 * by the next right-click); HERE immediately translates the selection so coord A
 * lands on the looked-at position.
 */
public class PacketMoveCoords {

    public enum Which {
        A,
        B,
        ALL,
        HERE
    }

    public final Which which;
    public final BlockPos lookingAt;

    public PacketMoveCoords(Which which, BlockPos lookingAt) {
        this.which = which;
        this.lookingAt = lookingAt;
    }

    public static void encode(PacketMoveCoords pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.which);
        buf.writeBlockPos(pkt.lookingAt);
    }

    public static PacketMoveCoords decode(FriendlyByteBuf buf) {
        return new PacketMoveCoords(buf.readEnum(Which.class), buf.readBlockPos());
    }

    public static void handle(PacketMoveCoords pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;

            var cfg = state.mutate();
            if (cfg.locked) {
                MMUtils.sendErrorToPlayer(player, "mm.info.locked");
                return;
            }

            switch (pkt.which) {
                case A -> {
                    cfg.action = PendingAction.MOVING_COORDS;
                    cfg.coordAOffset = new Vector3i();
                    cfg.coordBOffset = null;
                    cfg.coordCOffset = null;
                }
                case B -> {
                    cfg.action = PendingAction.MOVING_COORDS;
                    cfg.coordAOffset = null;
                    cfg.coordBOffset = new Vector3i();
                    cfg.coordCOffset = null;
                }
                case ALL -> {
                    cfg.action = PendingAction.MOVING_COORDS;
                    cfg.coordAOffset = offsetFrom(cfg.coordA, pkt.lookingAt);
                    cfg.coordBOffset = offsetFrom(cfg.coordB, pkt.lookingAt);
                    cfg.coordCOffset = offsetFrom(cfg.coordC, pkt.lookingAt);
                }
                case HERE -> moveHere(player, cfg, pkt.lookingAt);
            }

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }

    private static Vector3i offsetFrom(Location coord, BlockPos lookingAt) {
        if (coord == null) return null;
        return new Vector3i(
                coord.pos.getX() - lookingAt.getX(),
                coord.pos.getY() - lookingAt.getY(),
                coord.pos.getZ() - lookingAt.getZ());
    }

    private static void moveHere(ServerPlayer player, MMConfig cfg, BlockPos lookingAt) {
        boolean requiresC = (cfg.placeMode == PlaceMode.GEOMETRY && cfg.shape == ShapeType.CYLINDER)
                || cfg.placeMode == PlaceMode.COPYING
                || cfg.placeMode == PlaceMode.MOVING;

        if (cfg.coordA == null || cfg.coordB == null) return;
        if (!cfg.coordA.dimension.equals(cfg.coordB.dimension)) return;

        var dim = player.serverLevel().dimension();
        BlockPos a = cfg.coordA.pos;
        BlockPos newB = lookingAt.offset(
                cfg.coordB.pos.getX() - a.getX(), cfg.coordB.pos.getY() - a.getY(), cfg.coordB.pos.getZ() - a.getZ());

        if (requiresC) {
            if (cfg.coordC == null || !cfg.coordC.dimension.equals(cfg.coordA.dimension)) return;
            BlockPos newC = lookingAt.offset(
                    cfg.coordC.pos.getX() - a.getX(),
                    cfg.coordC.pos.getY() - a.getY(),
                    cfg.coordC.pos.getZ() - a.getZ());
            cfg.coordC = new Location(dim, newC);
        }

        cfg.coordA = new Location(dim, lookingAt);
        cfg.coordB = new Location(dim, newB);
        MMUtils.sendInfoToPlayer(player, "mm.info.coords_moved");
    }
}
