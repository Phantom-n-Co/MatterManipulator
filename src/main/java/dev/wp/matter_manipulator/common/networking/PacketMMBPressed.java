package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.ManipulatorActions;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sent when the player presses pick-block (middle mouse) while holding the manipulator.
 * GEOMETRY picks the palette block, EXCHANGING sets the whitelist (sneak) or target,
 * CABLES picks the cable block. COPYING/MOVING do nothing.
 */
public class PacketMMBPressed {

    private static final double MAX_PICK_DISTANCE_SQ = 7 * 7;

    public final BlockPos pos;
    public final boolean hasHit;

    public PacketMMBPressed(BlockPos pos, boolean hasHit) {
        this.pos = pos;
        this.hasHit = hasHit;
    }

    public static void encode(PacketMMBPressed pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeBoolean(pkt.hasHit);
    }

    public static PacketMMBPressed decode(FriendlyByteBuf buf) {
        return new PacketMMBPressed(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(PacketMMBPressed pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;
            if (!pkt.hasHit) return;
            if (player.distanceToSqr(pkt.pos.getX() + 0.5, pkt.pos.getY() + 0.5, pkt.pos.getZ() + 0.5)
                    > MAX_PICK_DISTANCE_SQ) return;

            // Note: lock only protects positions; block picking is always allowed.
            var cfg = state.mutate();
            boolean sneak = player.isShiftKeyDown();
            switch (cfg.placeMode) {
                case GEOMETRY -> ManipulatorActions.pickBlock(player, cfg, cfg.blockSelectMode, pkt.pos, sneak);
                case EXCHANGING -> {
                    if (sneak) {
                        ManipulatorActions.exchangeSetWhitelist(player, cfg, pkt.pos);
                    } else {
                        ManipulatorActions.exchangeSetTarget(player, cfg, pkt.pos);
                    }
                }
                case CABLES -> ManipulatorActions.pickCable(player, cfg, pkt.pos);
                default -> {}
            }

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
