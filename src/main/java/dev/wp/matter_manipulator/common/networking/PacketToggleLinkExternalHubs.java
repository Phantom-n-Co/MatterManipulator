package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

/** Server-bound: toggles the COPYING "link external wireless hubs" smart-copy option. */
public class PacketToggleLinkExternalHubs {
    public PacketToggleLinkExternalHubs() {}

    public static void encode(PacketToggleLinkExternalHubs pkt, FriendlyByteBuf buf) {}

    public static PacketToggleLinkExternalHubs decode(FriendlyByteBuf buf) {
        return new PacketToggleLinkExternalHubs();
    }

    public static void handle(PacketToggleLinkExternalHubs pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.linkExternalHubs = !cfg.linkExternalHubs;

            player.sendSystemMessage(Component.translatable(
                    MODID + ".info.link_external_hubs", cfg.linkExternalHubs ? "ON" : "OFF"));

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
