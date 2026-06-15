package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.common.building.BuildGenerator;
import dev.wp.matter_manipulator.common.building.PendingBlock;
import dev.wp.matter_manipulator.common.building.PendingBuild;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMCapability;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import dev.wp.matter_manipulator.common.items.manipulator.PlaceMode;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client requests the server to start a build based on the current MMConfig.
 */
public class PacketStartBuild {

    public PacketStartBuild() {}

    public static void encode(PacketStartBuild pkt, FriendlyByteBuf buf) {}

    public static PacketStartBuild decode(FriendlyByteBuf buf) {
        return new PacketStartBuild();
    }

    public static void handle(PacketStartBuild pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) execute(player);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void execute(ServerPlayer player) {
        var held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemMatterManipulator item)) return;

        var state = MMState.of(held);
        var cfg = state.getConfig();
        var tier = item.getTier();

        if (cfg.placeMode == PlaceMode.GEOMETRY && !state.hasCapability(tier, MMCapability.ALLOW_GEOMETRY)) return;
        if (cfg.placeMode == PlaceMode.COPYING && !state.hasCapability(tier, MMCapability.ALLOW_COPYING)) return;
        if (cfg.placeMode == PlaceMode.MOVING && !state.hasCapability(tier, MMCapability.ALLOW_MOVING)) return;
        if (cfg.placeMode == PlaceMode.EXCHANGING && !state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING)) return;
        if (cfg.placeMode == PlaceMode.CABLES && !state.hasCapability(tier, MMCapability.ALLOW_CABLES)) return;

        var level = player.serverLevel();
        if (cfg.coordA != null && !cfg.coordA.dimension.equals(level.dimension())) {
            player.sendSystemMessage(Component.literal("Coords must be in the current dimension."));
            return;
        }

        List<PendingBlock> blocks = BuildGenerator.generateBlocks(cfg, level);

        if (blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal("No blocks to build. Check selection and palette."));
            return;
        }

        BlockPos anchor = (cfg.placeMode == PlaceMode.COPYING || cfg.placeMode == PlaceMode.MOVING)
                ? cfg.coordC.pos
                : BlockPos.ZERO;

        BlockPos sourceMin = null, sourceMax = null;
        if (cfg.placeMode == PlaceMode.MOVING) {
            var a = cfg.coordA.pos;
            var b = cfg.coordB.pos;
            sourceMin = new BlockPos(
                    Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
            sourceMax = new BlockPos(
                    Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        }

        PendingBuild.start(
                player,
                tier,
                blocks,
                anchor,
                cfg.placeMode == PlaceMode.MOVING,
                sourceMin,
                sourceMax,
                cfg.placeMode == PlaceMode.CABLES);
    }
}
