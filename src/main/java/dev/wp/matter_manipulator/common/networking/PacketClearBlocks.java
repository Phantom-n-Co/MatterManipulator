package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketClearBlocks {

    public PacketClearBlocks() {}

    public static void encode(PacketClearBlocks pkt, FriendlyByteBuf buf) {}

    public static PacketClearBlocks decode(FriendlyByteBuf buf) {
        return new PacketClearBlocks();
    }

    public static void handle(PacketClearBlocks pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.of(held);
            var cfg = state.mutate();
            cfg.corners = new WeightedSpecList();
            cfg.edges = new WeightedSpecList();
            cfg.faces = new WeightedSpecList();
            cfg.volumes = new WeightedSpecList();
            state.saveIfDirty();
            player.sendSystemMessage(Component.literal("Palette cleared."));
        });
        ctx.get().setPacketHandled(true);
    }
}
