package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class PacketSetArraySpan {
    private final int index;
    private final int value;

    public PacketSetArraySpan(int index, int value) {
        this.index = index;
        this.value = value;
    }

    public PacketSetArraySpan(FriendlyByteBuf buf) {
        this.index = buf.readInt();
        this.value = buf.readInt();
    }

    public static PacketSetArraySpan decode(FriendlyByteBuf buf) {
        return new PacketSetArraySpan(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(index);
        buf.writeInt(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            var stack = player.getMainHandItem();
            var state = MMState.of(stack);
            if (index >= 0 && index < 3) {
                var cfg = state.mutate();
                cfg.arraySpan[index] = value;
                state.saveIfDirty();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
