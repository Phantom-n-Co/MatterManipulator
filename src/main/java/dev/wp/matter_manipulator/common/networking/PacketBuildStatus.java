package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.client.render.GhostBlockRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client bound: positions (BlockPos.asLong) of blocks the build failed on.
 * Rendered as red (error) / yellow (warning) hints that expire after the
 * configured statusExpiration. Sending two empty arrays clears the hints.
 */
public class PacketBuildStatus {

    public final long[] errors;
    public final long[] warnings;

    public PacketBuildStatus(long[] errors, long[] warnings) {
        this.errors = errors;
        this.warnings = warnings;
    }

    public static void encode(PacketBuildStatus pkt, FriendlyByteBuf buf) {
        buf.writeLongArray(pkt.errors);
        buf.writeLongArray(pkt.warnings);
    }

    public static PacketBuildStatus decode(FriendlyByteBuf buf) {
        return new PacketBuildStatus(buf.readLongArray(), buf.readLongArray());
    }

    public static void handle(PacketBuildStatus pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                        () -> GhostBlockRenderer.setStatusHints(pkt.errors, pkt.warnings)));
        ctx.get().setPacketHandled(true);
    }
}
