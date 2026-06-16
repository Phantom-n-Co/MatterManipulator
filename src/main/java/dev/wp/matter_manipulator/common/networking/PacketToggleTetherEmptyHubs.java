package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-bound: toggles the COPYING "tether empty wireless hubs to the source" smart-copy option. */
public class PacketToggleTetherEmptyHubs {
    public PacketToggleTetherEmptyHubs() {}

    public static void encode(PacketToggleTetherEmptyHubs pkt, FriendlyByteBuf buf) {}

    public static PacketToggleTetherEmptyHubs decode(FriendlyByteBuf buf) {
        return new PacketToggleTetherEmptyHubs();
    }

    public static void handle(PacketToggleTetherEmptyHubs pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.tetherEmptyHubs = !cfg.tetherEmptyHubs;

            player.sendSystemMessage(Component.translatable(
                    "mm.info.tether_empty_hubs", cfg.tetherEmptyHubs ? "ON" : "OFF"));

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
