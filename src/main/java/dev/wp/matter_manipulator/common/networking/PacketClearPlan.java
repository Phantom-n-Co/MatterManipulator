package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.utils.MMUtils;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

/** Clears the sending player's plan patterns from the uplink their held manipulator is bound to. */
public class PacketClearPlan {
    public PacketClearPlan() {}

    public static void encode(PacketClearPlan pkt, FriendlyByteBuf buf) {}

    public static PacketClearPlan decode(FriendlyByteBuf buf) {
        return new PacketClearPlan();
    }

    public static void handle(PacketClearPlan pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;
            var cfg = state.getConfig();
            if (!Mods.AE2 || cfg.uplink == null) {
                MMUtils.sendErrorToPlayer(player, MODID + "info.error.no_uplink");
                return;
            }
            int cleared = AECompat.uplinkClearPlan(player.serverLevel(), cfg.uplink, player.getUUID());
            if (cleared < 0) {
                MMUtils.sendErrorToPlayer(player, MODID + "info.error.no_uplink");
            } else {
                MMUtils.sendInfoToPlayer(player, MODID + "info.plan_cleared", cleared);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
