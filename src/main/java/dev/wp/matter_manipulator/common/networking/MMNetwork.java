package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.MMMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MMNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel CHANNEL;

    private static int nextId = 0;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                MMMod.id("main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        reg(PacketSetCoord.class, PacketSetCoord::encode, PacketSetCoord::decode, PacketSetCoord::handle);
        reg(PacketSetConfig.class, PacketSetConfig::encode, PacketSetConfig::decode, PacketSetConfig::handle);
        reg(PacketConfigAction.class, PacketConfigAction::encode, PacketConfigAction::decode, PacketConfigAction::handle);
        reg(
                PacketSetPendingAction.class,
                PacketSetPendingAction::encode,
                PacketSetPendingAction::decode,
                PacketSetPendingAction::handle);
        reg(PacketTransformAction.class, PacketTransformAction::encode, PacketTransformAction::decode, PacketTransformAction::handle);
        reg(PacketSetArraySpan.class, PacketSetArraySpan::encode, PacketSetArraySpan::decode, PacketSetArraySpan::handle);
        reg(PacketMMBPressed.class, PacketMMBPressed::encode, PacketMMBPressed::decode, PacketMMBPressed::handle);
        reg(PacketMoveCoords.class, PacketMoveCoords::encode, PacketMoveCoords::decode, PacketMoveCoords::handle);
        reg(PacketBuildStatus.class, PacketBuildStatus::encode, PacketBuildStatus::decode, PacketBuildStatus::handle);
        reg(
                PacketRequestRequiredItems.class,
                PacketRequestRequiredItems::encode,
                PacketRequestRequiredItems::decode,
                PacketRequestRequiredItems::handle);
        reg(
                PacketSetCoordDirect.class,
                PacketSetCoordDirect::encode,
                PacketSetCoordDirect::decode,
                PacketSetCoordDirect::handle);
        reg(PacketSignal.class, PacketSignal::encode, PacketSignal::decode, PacketSignal::handle);
    }

    private static <T> void reg(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encode,
            Function<FriendlyByteBuf, T> decode,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handle) {
        CHANNEL.registerMessage(nextId++, type, encode, decode, handle);
    }
}
