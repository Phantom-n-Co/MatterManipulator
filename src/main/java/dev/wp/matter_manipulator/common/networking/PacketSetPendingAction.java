package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.items.manipulator.BlockSelectMode;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PendingAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Sets a pending action on the server, optionally also updating blockSelectMode. */
public class PacketSetPendingAction {

    @Nullable private final PendingAction action;
    @Nullable private final BlockSelectMode selectMode;

    public PacketSetPendingAction(@Nullable PendingAction action, @Nullable BlockSelectMode selectMode) {
        this.action = action;
        this.selectMode = selectMode;
    }

    public static void encode(PacketSetPendingAction pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.action != null);
        if (pkt.action != null) buf.writeEnum(pkt.action);
        buf.writeBoolean(pkt.selectMode != null);
        if (pkt.selectMode != null) buf.writeEnum(pkt.selectMode);
    }

    public static PacketSetPendingAction decode(FriendlyByteBuf buf) {
        PendingAction action = buf.readBoolean() ? buf.readEnum(PendingAction.class) : null;
        BlockSelectMode mode = buf.readBoolean() ? buf.readEnum(BlockSelectMode.class) : null;
        return new PacketSetPendingAction(action, mode);
    }

    public static void handle(PacketSetPendingAction pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.of(held);
            var cfg = state.mutate();
            cfg.action = pkt.action;
            if (pkt.selectMode != null) cfg.blockSelectMode = pkt.selectMode;
            state.saveIfDirty();

            if (pkt.action != null) {
                player.sendSystemMessage(Component.literal(
                    "Right-click a block to: " + actionDescription(pkt.action)));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String actionDescription(PendingAction action) {
        return switch (action) {
            case GEOM_SELECTING_BLOCK -> "select block for palette";
            case MARK_COPY_A -> "mark copy corner A";
            case MARK_COPY_B -> "mark copy corner B";
            case MARK_CUT_A -> "mark cut corner A";
            case MARK_CUT_B -> "mark cut corner B";
            case MARK_PASTE_A -> "mark paste corner A";
            case MARK_PASTE_B -> "mark paste corner B";
            case EXCH_ADD_REPLACE -> "add block to replace whitelist";
            case EXCH_SET_REPLACE -> "set replace whitelist";
            case EXCH_SET_TARGET -> "set block to replace with";
            case PICK_CABLE -> "pick cable";
            case SELECTING_CORNER_B -> "set corner B";
            case MOVING_COORDS -> "commit the moved coordinates";
            case MARK_ARRAY -> "mark the array span";
        };
    }
}
