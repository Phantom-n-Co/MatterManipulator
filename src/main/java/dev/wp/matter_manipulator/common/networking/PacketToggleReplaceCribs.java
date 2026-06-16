package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

/** Server-bound: toggles the COPYING "replace CRIBs with proxies" smart-copy option. */
public class PacketToggleReplaceCribs {
    public PacketToggleReplaceCribs() {}

    public static void encode(PacketToggleReplaceCribs pkt, FriendlyByteBuf buf) {}

    public static PacketToggleReplaceCribs decode(FriendlyByteBuf buf) {
        return new PacketToggleReplaceCribs();
    }

    public static void handle(PacketToggleReplaceCribs pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            cfg.replaceCribsWithProxies = !cfg.replaceCribsWithProxies;

            player.sendSystemMessage(Component.translatable(
                    MODID + "info.replace_cribs", cfg.replaceCribsWithProxies ? "ON" : "OFF"));

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
