package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Clears the exchange-mode replace whitelist. */
public class PacketClearWhitelist {

    public PacketClearWhitelist() {}

    public static void encode(PacketClearWhitelist pkt, FriendlyByteBuf buf) {}

    public static PacketClearWhitelist decode(FriendlyByteBuf buf) {
        return new PacketClearWhitelist();
    }

    public static void handle(PacketClearWhitelist pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;

            var cfg = state.mutate();
            cfg.replaceWhitelist = new WeightedSpecList();
            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
