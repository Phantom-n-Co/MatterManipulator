package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSetArraySpan {
    private final int index;
    private final int value;

    public PacketSetArraySpan(int index, int value) {
        this.index = index;
        this.value = value;
    }

    public static PacketSetArraySpan decode(FriendlyByteBuf buf) {
        return new PacketSetArraySpan(buf.readInt(), buf.readInt());
    }

    public static void encode(PacketSetArraySpan pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.index);
        buf.writeInt(pkt.value);
    }

    public static void handle(PacketSetArraySpan pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            var stack = player.getMainHandItem();
            var state = MMState.of(stack);
            if (pkt.index >= 0 && pkt.index < 3) {
                var cfg = state.mutate();
                cfg.arraySpan[pkt.index] = pkt.value;
                state.saveIfDirty();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
