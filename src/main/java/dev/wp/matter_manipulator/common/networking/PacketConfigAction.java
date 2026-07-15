package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.data.WeightedSpecList;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.PlaceMode;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.RemoveMode;
import dev.wp.matter_manipulator.common.items.manipulator.MMTypes.ShapeType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

/**
 * Server-bound: a single held-manipulator config mutation, picked by {@link Action}. Folds together the dozen
 * near-identical toggle/clear/reset/set packets that all did {@code ofHeld → mutate → one change → saveIfDirty}.
 * The {@code arg} carries an enum ordinal for the SET_* actions and is ignored by the rest.
 */
public class PacketConfigAction {
    public enum Action {
        TOGGLE_LOCK {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.locked = !cfg.locked;
                return Component.literal("Matter Manipulator positions " + (cfg.locked ? "LOCKED" : "UNLOCKED"));
            }
        },
        TOGGLE_REPLACE_CRIBS {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.replaceCribsWithProxies = !cfg.replaceCribsWithProxies;
                return Component.translatable(MODID + ".info.replace_cribs", cfg.replaceCribsWithProxies ? "ON" : "OFF");
            }
        },
        TOGGLE_LINK_EXTERNAL_HUBS {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.linkExternalHubs = !cfg.linkExternalHubs;
                return Component.translatable(MODID + ".info.link_external_hubs", cfg.linkExternalHubs ? "ON" : "OFF");
            }
        },
        TOGGLE_TETHER_EMPTY_HUBS {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.tetherEmptyHubs = !cfg.tetherEmptyHubs;
                return Component.translatable(MODID + ".info.tether_empty_hubs", cfg.tetherEmptyHubs ? "ON" : "OFF");
            }
        },
        CLEAR_BLOCKS {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.corners = new WeightedSpecList();
                cfg.edges = new WeightedSpecList();
                cfg.faces = new WeightedSpecList();
                cfg.volumes = new WeightedSpecList();
                return Component.literal("Palette cleared.");
            }
        },
        CLEAR_COORDS {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.clearCoords();
                return Component.literal("Matter Manipulator coordinates cleared.");
            }
        },
        CLEAR_WHITELIST {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.replaceWhitelist = new WeightedSpecList();
                return null;
            }
        },
        RESET_ARRAY {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.arraySpan = new int[] { 0, 0, 0 };
                return null;
            }
        },
        RESET_TRANSFORM {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.transform = new MMTransform();
                cfg.arraySpan = new int[] { 0, 0, 0 };
                return Component.literal("Matter Manipulator transform reset.");
            }
        },
        SET_PLACE_MODE {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.placeMode = PlaceMode.values()[arg];
                return null;
            }
        },
        SET_REMOVE_MODE {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.removeMode = RemoveMode.values()[arg];
                return null;
            }
        },
        SET_SHAPE {
            @Override
            Component apply(MMConfig cfg, int arg) {
                cfg.shape = ShapeType.values()[arg];
                return null;
            }
        };

        abstract Component apply(MMConfig cfg, int arg);
    }

    private final Action action;
    private final int arg;

    public PacketConfigAction(Action action) {
        this(action, 0);
    }

    public PacketConfigAction(Action action, int arg) {
        this.action = action;
        this.arg = arg;
    }

    public static void encode(PacketConfigAction pkt, FriendlyByteBuf buf) {
        buf.writeEnum(pkt.action);
        buf.writeVarInt(pkt.arg);
    }

    public static PacketConfigAction decode(FriendlyByteBuf buf) {
        return new PacketConfigAction(buf.readEnum(Action.class), buf.readVarInt());
    }

    public static void handle(PacketConfigAction pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var state = MMState.ofHeld(player.getMainHandItem());
            if (state == null) return;

            Component msg = pkt.action.apply(state.mutate(), pkt.arg);
            if (msg != null) player.sendSystemMessage(msg);

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }
}
