package dev.wp.matter_manipulator.common.networking;

import dev.wp.matter_manipulator.MMMod;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class MMNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static SimpleChannel CHANNEL;

    private static int nextId = 0;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                MMMod.id("main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

        CHANNEL.registerMessage(
                nextId++, PacketSetCoord.class, PacketSetCoord::encode, PacketSetCoord::decode, PacketSetCoord::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetConfig.class,
                PacketSetConfig::encode,
                PacketSetConfig::decode,
                PacketSetConfig::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketStartBuild.class,
                PacketStartBuild::encode,
                PacketStartBuild::decode,
                PacketStartBuild::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketCancelBuild.class,
                PacketCancelBuild::encode,
                PacketCancelBuild::decode,
                PacketCancelBuild::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetPlaceMode.class,
                PacketSetPlaceMode::encode,
                PacketSetPlaceMode::decode,
                PacketSetPlaceMode::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetRemoveMode.class,
                PacketSetRemoveMode::encode,
                PacketSetRemoveMode::decode,
                PacketSetRemoveMode::handle);

        CHANNEL.registerMessage(
                nextId++, PacketSetShape.class, PacketSetShape::encode, PacketSetShape::decode, PacketSetShape::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetPendingAction.class,
                PacketSetPendingAction::encode,
                PacketSetPendingAction::decode,
                PacketSetPendingAction::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketClearBlocks.class,
                PacketClearBlocks::encode,
                PacketClearBlocks::decode,
                PacketClearBlocks::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketToggleLock.class,
                PacketToggleLock::encode,
                PacketToggleLock::decode,
                PacketToggleLock::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketClearCoords.class,
                PacketClearCoords::encode,
                PacketClearCoords::decode,
                PacketClearCoords::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketResetTransform.class,
                PacketResetTransform::encode,
                PacketResetTransform::decode,
                PacketResetTransform::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketRotateTransform.class,
                PacketRotateTransform::encode,
                PacketRotateTransform::decode,
                PacketRotateTransform::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketToggleFlip.class,
                PacketToggleFlip::encode,
                PacketToggleFlip::decode,
                PacketToggleFlip::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetArraySpan.class,
                PacketSetArraySpan::encode,
                PacketSetArraySpan::decode,
                PacketSetArraySpan::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketMMBPressed.class,
                PacketMMBPressed::encode,
                PacketMMBPressed::decode,
                PacketMMBPressed::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketMoveCoords.class,
                PacketMoveCoords::encode,
                PacketMoveCoords::decode,
                PacketMoveCoords::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketClearWhitelist.class,
                PacketClearWhitelist::encode,
                PacketClearWhitelist::decode,
                PacketClearWhitelist::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketResetArray.class,
                PacketResetArray::encode,
                PacketResetArray::decode,
                PacketResetArray::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketBuildStatus.class,
                PacketBuildStatus::encode,
                PacketBuildStatus::decode,
                PacketBuildStatus::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketRequestRequiredItems.class,
                PacketRequestRequiredItems::encode,
                PacketRequestRequiredItems::decode,
                PacketRequestRequiredItems::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketSetCoordDirect.class,
                PacketSetCoordDirect::encode,
                PacketSetCoordDirect::decode,
                PacketSetCoordDirect::handle);

        CHANNEL.registerMessage(
                nextId++,
                PacketClearPlan.class,
                PacketClearPlan::encode,
                PacketClearPlan::decode,
                PacketClearPlan::handle);
    }
}
