package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.items.manipulator.MMConfig;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.ManipulatorActions;
import dev.wp.matter_manipulator.common.items.manipulator.PendingAction;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import dev.wp.matter_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import org.joml.Vector3i;

import java.util.function.Supplier;

import static dev.wp.matter_manipulator.MMMod.MODID;

public class PacketSetCoord {
    /**
     * 0=A, 1=B, 2=C
     */
    public final int which;

    public final Location loc;

    public PacketSetCoord(int which, Location loc) {
        this.which = which;
        this.loc = loc;
    }

    public static void encode(PacketSetCoord pkt, FriendlyByteBuf buf) {
        buf.writeByte(pkt.which);
        buf.writeResourceLocation(pkt.loc.dimension.location());
        buf.writeBlockPos(pkt.loc.pos);
    }

    public static PacketSetCoord decode(FriendlyByteBuf buf) {
        int which = buf.readByte();
        var dimKey = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        var pos = buf.readBlockPos();
        return new PacketSetCoord(which, new Location(dimKey, pos));
    }

    public static void handle(PacketSetCoord pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var held = player.getMainHandItem();
            var state = MMState.ofHeld(held);
            if (state == null) return;

            var cfg = state.mutate();

            if (cfg.locked) {
                player.sendSystemMessage(
                        Component.literal("Matter Manipulator positions are LOCKED. Toggle to change."));
                return;
            }

            if (cfg.action != null) {
                handlePendingAction(player, held, state, cfg, pkt.loc);
            } else if (isAreaMode(cfg.placeMode)) {
                // First click in GEOMETRY/EXCHANGING/CABLES: anchor corner A, wait for corner B
                cfg.coordA = pkt.loc;
                cfg.coordB = null;
                cfg.action = PendingAction.SELECTING_CORNER_B;
                player.sendSystemMessage(Component.literal(
                        "Corner A set at " + pkt.loc.pos.toShortString() + ". Right-click to set corner B."));
            } else {
                // Direct coord set for other modes (COPYING, MOVING)
                // In these modes, right-click defaults to setting coord C (Paste target) if no action is active.
                int effectiveWhich = pkt.which;
                if (effectiveWhich == 0 && (cfg.placeMode == PlaceMode.COPYING || cfg.placeMode == PlaceMode.MOVING)) {
                    effectiveWhich = 2;
                }

                setCoord(cfg, effectiveWhich, pkt.loc);
                String label = switch (effectiveWhich) {
                    case 0 -> "A";
                    case 1 -> "B";
                    default -> "C";
                };
                player.sendSystemMessage(
                        Component.literal("Coord " + label + " set to " + pkt.loc.pos.toShortString()));
            }

            state.saveIfDirty();
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * True for modes where the user selects an area with two sequential right-clicks.
     */
    private static boolean isAreaMode(PlaceMode mode) {
        return mode == PlaceMode.GEOMETRY || mode == PlaceMode.EXCHANGING || mode == PlaceMode.CABLES;
    }

    private static void setCoord(MMConfig cfg, int which, Location loc) {
        switch (which) {
            case 0 -> cfg.coordA = loc;
            case 1 -> cfg.coordB = loc;
            case 2 -> cfg.coordC = loc;
        }
    }

    private static void handlePendingAction(
            ServerPlayer player, ItemStack held, MMState state, MMConfig cfg, Location loc) {
        switch (cfg.action) {
            case SELECTING_CORNER_B -> {
                // Second click: confirm corner B
                cfg.coordB = loc;
                cfg.action = null;
                player.sendSystemMessage(Component.literal("Corner B set at " + loc.pos.toShortString() + "."));
            }
            case MOVING_COORDS -> {
                // Commit: coords with non-null offsets have been tracking the crosshair
                var level = player.serverLevel();
                BlockPos a = cfg.coordAOffset != null ? cfg.getCoordA(level, loc.pos) : null;
                BlockPos b = cfg.coordBOffset != null ? cfg.getCoordB(level, loc.pos) : null;
                BlockPos c = cfg.coordCOffset != null ? cfg.getCoordC(level, loc.pos) : null;
                if (a != null) cfg.coordA = new Location(loc.dimension, a);
                if (b != null) cfg.coordB = new Location(loc.dimension, b);
                if (c != null) cfg.coordC = new Location(loc.dimension, c);
                cfg.coordAOffset = null;
                cfg.coordBOffset = null;
                cfg.coordCOffset = null;
                cfg.action = null;
                MMUtils.sendInfoToPlayer(player, MODID + "info.coords_moved");
            }
            case MARK_ARRAY -> {
                if (cfg.coordA == null || cfg.coordB == null || !cfg.coordA.dimension.equals(cfg.coordB.dimension)) {
                    MMUtils.sendErrorToPlayer(player, MODID + "info.error.cannot_mark_copy");
                } else if (cfg.coordC == null || !cfg.coordC.dimension.equals(cfg.coordA.dimension)) {
                    MMUtils.sendErrorToPlayer(player, MODID + "info.error.cannot_mark_paste");
                } else {
                    Vector3i mult = cfg.getArrayMult(loc.pos);
                    cfg.arraySpan = new int[] { mult.x, mult.y, mult.z };
                    MMUtils.sendInfoToPlayer(player, MODID + "info.array_marked", mult.x, mult.y, mult.z);
                }
                cfg.action = null;
            }
            case GEOM_SELECTING_BLOCK -> {
                ManipulatorActions.pickBlock(player, cfg, cfg.blockSelectMode, loc.pos, player.isShiftKeyDown());
                cfg.action = null;
            }
            case MARK_COPY_A -> {
                cfg.coordA = loc;
                cfg.action = PendingAction.MARK_COPY_B;
                player.sendSystemMessage(Component.literal("Copy A set. Right-click to set copy B."));
            }
            case MARK_COPY_B -> {
                cfg.coordB = loc;
                cfg.action = null;
                player.sendSystemMessage(Component.literal("Copy B set."));
            }
            case MARK_CUT_A -> {
                cfg.coordA = loc;
                cfg.action = PendingAction.MARK_CUT_B;
                player.sendSystemMessage(Component.literal("Cut A set. Right-click to set cut B."));
            }
            case MARK_CUT_B -> {
                cfg.coordB = loc;
                cfg.action = null;
                player.sendSystemMessage(Component.literal("Cut B set."));
            }
            case MARK_PASTE_A -> {
                cfg.coordC = loc;
                cfg.action = null;
                player.sendSystemMessage(Component.literal("Paste target set."));
            }
            case EXCH_SET_TARGET -> {
                ManipulatorActions.exchangeSetTarget(player, cfg, loc.pos);
                cfg.action = null;
            }
            case EXCH_ADD_REPLACE -> {
                ManipulatorActions.exchangeAddWhitelist(player, cfg, loc.pos);
                cfg.action = null;
            }
            case EXCH_SET_REPLACE -> {
                ManipulatorActions.exchangeSetWhitelist(player, cfg, loc.pos);
                cfg.action = null;
            }
            case PICK_CABLE -> {
                ManipulatorActions.pickCable(player, cfg, loc.pos);
                cfg.action = null;
            }
        }
    }
}
