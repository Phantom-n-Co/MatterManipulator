package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSetConfig {
    public final CompoundTag configNbt;

    public PacketSetConfig(CompoundTag configNbt) {
        this.configNbt = configNbt;
    }

    public static void encode(PacketSetConfig pkt, FriendlyByteBuf buf) {
        buf.writeNbt(pkt.configNbt);
    }

    public static PacketSetConfig decode(FriendlyByteBuf buf) {
        return new PacketSetConfig(buf.readNbt());
    }

    public static void handle(PacketSetConfig pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null || pkt.configNbt == null) return;

            MMConfig cfg = MMConfig.fromNBT(pkt.configNbt);
            state.saveConfig(cfg);
        });
        ctx.get().setPacketHandled(true);
    }
}
