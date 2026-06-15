package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSetConfig {
    public final String configJson;

    public PacketSetConfig(String configJson) {
        this.configJson = configJson;
    }

    public static void encode(PacketSetConfig pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.configJson, 32768);
    }

    public static PacketSetConfig decode(FriendlyByteBuf buf) {
        return new PacketSetConfig(buf.readUtf(32768));
    }

    public static void handle(PacketSetConfig pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            try {
                MMConfig cfg = MMState.GSON.fromJson(pkt.configJson, MMConfig.class);
                state.saveConfig(cfg);
            } catch (Exception e) {
                // Ignore bad packets
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
