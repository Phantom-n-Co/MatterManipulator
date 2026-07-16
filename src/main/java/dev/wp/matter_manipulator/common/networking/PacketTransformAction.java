package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.MMState;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketTransformAction {
    public enum Action {
        ROTATE_X_POS, ROTATE_X_NEG, ROTATE_Y_POS, ROTATE_Y_NEG, ROTATE_Z_POS, ROTATE_Z_NEG,
        FLIP_X, FLIP_Y, FLIP_Z
    }

    private final Action action;

    public PacketTransformAction(Action action) {
        this.action = action;
    }

    public static void encode(PacketTransformAction pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
    }

    public static PacketTransformAction decode(FriendlyByteBuf buf) {
        return new PacketTransformAction(buf.readEnum(Action.class));
    }

    public static void handle(PacketTransformAction pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();
            switch (pkt.action) {
                case ROTATE_X_POS -> cfg.rotate(Direction.EAST, 1);
                case ROTATE_X_NEG -> cfg.rotate(Direction.EAST, -1);
                case ROTATE_Y_POS -> cfg.rotate(Direction.UP, 1);
                case ROTATE_Y_NEG -> cfg.rotate(Direction.UP, -1);
                case ROTATE_Z_POS -> cfg.rotate(Direction.SOUTH, 1);
                case ROTATE_Z_NEG -> cfg.rotate(Direction.SOUTH, -1);
                case FLIP_X -> cfg.transform.flipX = !cfg.transform.flipX;
                case FLIP_Y -> cfg.transform.flipY = !cfg.transform.flipY;
                case FLIP_Z -> cfg.transform.flipZ = !cfg.transform.flipZ;
            }
            cfg.transform.uncache();

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
