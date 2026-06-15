package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.PendingBuild;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class PacketCancelBuild {

    public PacketCancelBuild() {}

    public static void encode(PacketCancelBuild pkt, FriendlyByteBuf buf) {}

    public static PacketCancelBuild decode(FriendlyByteBuf buf) {
        return new PacketCancelBuild();
    }

    public static void handle(PacketCancelBuild pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) execute(player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void execute(ServerPlayer player) {
        PendingBuild.cancel(player);
        player.sendSystemMessage(Component.literal("Build cancelled."));
    }
}
