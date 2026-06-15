package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Registry of {@link CopyHandler}s and the entry points the build loop uses to reduce a captured
 * block entity to its copyable configuration.
 *
 * <p>The policy is allowlist-by-default: {@link #buildCopyTag} keeps only the BE identity unless a
 * handler contributes more, so an unrecognized block entity pastes blank rather than dragging its
 * full internal state to the new location.
 */
public final class CopyHandlers {
    private static final List<CopyHandler> HANDLERS = new ArrayList<>();

    private CopyHandlers() {}

    public static void register(CopyHandler handler) {
        HANDLERS.add(handler);
    }

    /** Registers the built-in handlers. Modded handlers load only when their mod is present. */
    public static void init() {
        register(new VanillaMiscHandler());
        if (ModList.get().isLoaded("gtceu")) {
            register(new GTCompat.GTCopyHandler());
        }
        if (ModList.get().isLoaded("ae2")) {
            register(new AECompat.AECopyHandler());
        }
    }

    /**
     * Builds the tag to load when COPYING the given block. Always carries the BE identity so the
     * block entity reconstructs as the right type; everything else must be opted in by a handler.
     */
    public static CompoundTag buildCopyTag(
            BlockState state, @Nullable BlockEntity be, CompoundTag full, MMInventory inv) {
        CompoundTag out = new CompoundTag();
        if (full.contains("id", Tag.TAG_STRING)) {
            out.putString("id", full.getString("id"));
        }
        for (CopyHandler handler : HANDLERS) {
            if (handler.matches(state, be, full)) {
                handler.contributeNbt(full, out, inv);
            }
        }
        return out;
    }

    /**
     * Lets handlers rewrite captured tile NBT to follow a COPYING/MOVING rotation/flip (e.g. cover
     * side remapping). Each handler self-guards, so this runs them all in order.
     */
    public static CompoundTag transformTileData(BlockState state, CompoundTag tileData, MMTransform transform) {
        CompoundTag out = tileData;
        for (CopyHandler handler : HANDLERS) {
            out = handler.transformTileData(state, out, transform);
        }
        return out;
    }

    /**
     * Lets handlers correct orientation that vanilla rotate/mirror and per-property facing can't express
     * (e.g. a GregTech multiblock's upwards facing), after the generic transform. Each handler
     * self-guards, so this runs them all in order.
     */
    public static BlockState transformBlockState(BlockState rotated, BlockState original, MMTransform transform) {
        BlockState out = rotated;
        for (CopyHandler handler : HANDLERS) {
            out = handler.transformBlockState(out, original, transform);
        }
        return out;
    }

    /** Runs every matching handler's post-load step (settings re-apply, cover/part charging). */
    public static void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be, CompoundTag full, MMInventory inv) {
        BlockState state = be.getBlockState();
        for (CopyHandler handler : HANDLERS) {
            if (handler.matches(state, be, full)) {
                handler.afterPaste(level, pos, be, full, inv);
            }
        }
    }
}
