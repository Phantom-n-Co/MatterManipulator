package dev.wp.matter_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Block-edit permission checks for the manipulator, mirroring the original mod's
 * {@code AbstractBuildable.isEditable}: a build must respect world-border / spawn protection and let
 * other mods (claims, region protection) veto each placement or removal through Forge's block events.
 *
 * <p>Stateless — the caller ({@link PendingBuild}) owns the "warn once per build" behaviour.
 */
public final class BuildProtection {
    private BuildProtection() {}

    /** True if the player may place a block at {@code pos} (world border, spawn protection, claim mods). */
    public static boolean canPlace(ServerLevel level, BlockPos pos, Player player) {
        if (!withinBounds(level, pos, player)) return false;
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        var event = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    /** True if the player may break the block at {@code pos} (world border, spawn protection, claim mods). */
    public static boolean canBreak(ServerLevel level, BlockPos pos, Player player) {
        if (!withinBounds(level, pos, player)) return false;
        var event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    private static boolean withinBounds(ServerLevel level, BlockPos pos, Player player) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        MinecraftServer server = level.getServer();
        return server == null || !server.isUnderSpawnProtection(level, pos, player);
    }
}
