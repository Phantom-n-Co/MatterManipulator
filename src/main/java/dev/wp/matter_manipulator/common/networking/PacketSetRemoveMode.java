package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.RemoveMode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSetRemoveMode {
    private final RemoveMode mode;

    public PacketSetRemoveMode(RemoveMode mode) {
        this.mode = mode;
    }

    public static void encode(PacketSetRemoveMode pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.mode);
    }

    public static PacketSetRemoveMode decode(FriendlyByteBuf buf) {
        return new PacketSetRemoveMode(buf.readEnum(RemoveMode.class));
    }

    public static void handle(PacketSetRemoveMode pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.of(held);
            state.mutate().removeMode = pkt.mode;
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
