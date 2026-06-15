package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class PacketToggleLock {

    public PacketToggleLock() {}

    public static void encode(PacketToggleLock pkt, FriendlyByteBuf buf) {}

    public static PacketToggleLock decode(FriendlyByteBuf buf) {
        return new PacketToggleLock();
    }

    public static void handle(PacketToggleLock pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.locked = !cfg.locked;

            player.sendSystemMessage(
                    Component.literal("Matter Manipulator positions " + (cfg.locked ? "LOCKED" : "UNLOCKED")));

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
