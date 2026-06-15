package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Resets the copy array span. */
public class PacketResetArray {
    public PacketResetArray() {}

    public static void encode(PacketResetArray pkt, FriendlyByteBuf buf) {}

    public static PacketResetArray decode(FriendlyByteBuf buf) {
        return new PacketResetArray();
    }

    public static void handle(PacketResetArray pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;

            var cfg = state.mutate();
            cfg.arraySpan = new int[] { 0, 0, 0 };
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
