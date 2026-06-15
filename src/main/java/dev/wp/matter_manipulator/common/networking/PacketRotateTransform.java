package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRotateTransform {
    private final Direction axis;
    private final boolean positive;

    public PacketRotateTransform(Direction axis, boolean positive) {
        this.axis = axis;
        this.positive = positive;
    }

    public static void encode(PacketRotateTransform pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.axis);
        buf.writeBoolean(pkt.positive);
    }

    public static PacketRotateTransform decode(FriendlyByteBuf buf) {
        return new PacketRotateTransform(buf.readEnum(Direction.class), buf.readBoolean());
    }

    public static void handle(PacketRotateTransform pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.rotate(pkt.axis, pkt.positive ? 1 : -1);

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
