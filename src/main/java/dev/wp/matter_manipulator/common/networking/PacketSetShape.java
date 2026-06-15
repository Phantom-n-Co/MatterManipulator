package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.ShapeType;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class PacketSetShape {

    private final ShapeType shape;

    public PacketSetShape(ShapeType shape) {
        this.shape = shape;
    }

    public static void encode(PacketSetShape pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.shape);
    }

    public static PacketSetShape decode(FriendlyByteBuf buf) {
        return new PacketSetShape(buf.readEnum(ShapeType.class));
    }

    public static void handle(PacketSetShape pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.of(held);
            state.mutate().shape = pkt.shape;
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
