package dev.wp.matter_manipulator.common.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Chat feedback helpers.
 */
public class MMUtils {

    public static void sendErrorToPlayer(Player player, Component message) {
        player.sendSystemMessage(colored(message, ChatFormatting.RED));
    }

    public static void sendErrorToPlayer(Player player, String key, Object... args) {
        sendErrorToPlayer(player, Component.translatable(key, args));
    }

    public static void sendWarningToPlayer(Player player, Component message) {
        player.sendSystemMessage(colored(message, ChatFormatting.GOLD));
    }

    public static void sendWarningToPlayer(Player player, String key, Object... args) {
        sendWarningToPlayer(player, Component.translatable(key, args));
    }

    public static void sendInfoToPlayer(Player player, Component message) {
        player.sendSystemMessage(colored(message, ChatFormatting.GRAY));
    }

    public static void sendInfoToPlayer(Player player, String key, Object... args) {
        sendInfoToPlayer(player, Component.translatable(key, args));
    }

    private static MutableComponent colored(Component message, ChatFormatting color) {
        return message.copy().withStyle(style -> style.getColor() == null ? style.withColor(color) : style);
    }
}
