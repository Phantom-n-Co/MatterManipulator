package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sets a coordinate to an absolute position, bypassing the pending-action and
 * area-selection semantics of {@link PacketSetCoord}. Used by the transform screen's
 * coordinate editors (the original's SetA/SetB/SetC messages).
 */
public class PacketSetCoordDirect {

    /**
     * 0=A, 1=B, 2=C
     */
    public final int which;
    public final BlockPos pos;

    public PacketSetCoordDirect(int which, BlockPos pos) {
        this.which = which;
        this.pos = pos;
    }

    public static void encode(PacketSetCoordDirect pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.which);
        buf.writeBlockPos(pkt.pos);
    }

    public static PacketSetCoordDirect decode(FriendlyByteBuf buf) {
        return new PacketSetCoordDirect(buf.readByte(), buf.readBlockPos());
    }

    public static void handle(PacketSetCoordDirect pkt, Supplier<NetworkEvent.Context> ctx) {
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

            var loc = new Location(player.level().dimension(), pkt.pos);
            switch (pkt.which) {
                case 0 -> {
                    cfg.coordA = loc;
                    cfg.coordAOffset = null;
                }
                case 1 -> {
                    cfg.coordB = loc;
                    cfg.coordBOffset = null;
                }
                case 2 -> {
                    cfg.coordC = loc;
                    cfg.coordCOffset = null;
                }
            }

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
