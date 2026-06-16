package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.PendingBuild;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-bound: sneak-right-clicking the manipulator toggles the build — starts one if none is running,
 * cancels it otherwise. The client can't see the server-side build state, so it sends this intent and
 * the server decides.
 */
public class PacketToggleBuild {
    public PacketToggleBuild() {}

    public static void encode(PacketToggleBuild pkt, FriendlyByteBuf buf) {}

    public static PacketToggleBuild decode(FriendlyByteBuf buf) {
        return new PacketToggleBuild();
    }

    public static void handle(PacketToggleBuild pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (PendingBuild.hasActiveBuild(player.getUUID())) {
                PacketCancelBuild.execute(player);
            } else {
                PacketStartBuild.execute(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
