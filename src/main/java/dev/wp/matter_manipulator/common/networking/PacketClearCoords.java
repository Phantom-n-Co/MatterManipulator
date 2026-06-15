package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClearCoords {
    public PacketClearCoords() {}

    public static void encode(PacketClearCoords pkt, FriendlyByteBuf buf) {}

    public static PacketClearCoords decode(FriendlyByteBuf buf) {
        return new PacketClearCoords();
    }

    public static void handle(PacketClearCoords pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.clearCoords();

            player.sendSystemMessage(Component.literal("Matter Manipulator coordinates cleared."));

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
