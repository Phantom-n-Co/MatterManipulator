package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketToggleFlip {
    private final int flipMask;

    public PacketToggleFlip(int flipMask) {
        this.flipMask = flipMask;
    }

    public static void encode(PacketToggleFlip pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.flipMask);
    }

    public static PacketToggleFlip decode(FriendlyByteBuf buf) {
        return new PacketToggleFlip(buf.readInt());
    }

    public static void handle(PacketToggleFlip pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            if ((pkt.flipMask & MMTransform.FLIP_X) != 0) cfg.transform.flipX = !cfg.transform.flipX;
            if ((pkt.flipMask & MMTransform.FLIP_Y) != 0) cfg.transform.flipY = !cfg.transform.flipY;
            if ((pkt.flipMask & MMTransform.FLIP_Z) != 0) cfg.transform.flipZ = !cfg.transform.flipZ;
            cfg.transform.uncache();

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
