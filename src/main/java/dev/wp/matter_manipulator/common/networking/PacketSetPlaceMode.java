package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSetPlaceMode {
    private final PlaceMode mode;

    public PacketSetPlaceMode(PlaceMode mode) {
        this.mode = mode;
    }

    public static void encode(PacketSetPlaceMode pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.mode);
    }

    public static PacketSetPlaceMode decode(FriendlyByteBuf buf) {
        return new PacketSetPlaceMode(buf.readEnum(PlaceMode.class));
    }

    public static void handle(PacketSetPlaceMode pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.of(held);
            state.mutate().placeMode = pkt.mode;
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
