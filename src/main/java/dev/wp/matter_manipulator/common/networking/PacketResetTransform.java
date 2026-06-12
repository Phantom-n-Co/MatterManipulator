package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketResetTransform {

    public PacketResetTransform() {}

    public static void encode(PacketResetTransform pkt, FriendlyByteBuf buf) {}

    public static PacketResetTransform decode(FriendlyByteBuf buf) {
        return new PacketResetTransform();
    }

    public static void handle(PacketResetTransform pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.transform = new MMTransform();
            cfg.arraySpan = new int[]{0, 0, 0};
            
            player.sendSystemMessage(Component.literal("Matter Manipulator transform reset."));
            
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
