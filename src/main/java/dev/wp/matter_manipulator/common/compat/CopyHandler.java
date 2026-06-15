package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A per-block-type rule for what survives a COPY. The manipulator no longer pastes a block
 * entity's full NBT; instead each handler explicitly allows the configuration it understands.
 * Anything no handler claims is dropped (see {@link CopyHandlers}).
 *
 * <p>Stored items are handled generically by {@code PendingBuild.injectContents} via the
 * item-handler capability, so handlers only deal with configuration, covers, and parts.
 */
public interface CopyHandler {

    /** Whether this handler applies to the given block. {@code fullTag} is the captured BE NBT. */
    boolean matches(BlockState state, @Nullable BlockEntity be, @Nullable CompoundTag fullTag);

    /**
     * Copies the configuration keys this handler allows from the captured tag into the tag that
     * will actually be loaded. The BE identity ({@code id}) is added by the registry, so handlers
     * only contribute their own keys. {@code inv} is provided for handlers that gate a kept key on
     * the player being able to pay for it (e.g. a jukebox record), consuming it when kept.
     */
    default void contributeNbt(CompoundTag full, CompoundTag out, MMInventory inv) {}

    /**
     * Rewrites the captured tile NBT to follow a COPYING/MOVING rotation/flip — e.g. moving covers
     * to the rotated sides. Runs at build generation, so the change is reflected in both the ghost
     * preview and the built block. Default no-op; return the (possibly mutated) tag.
     */
    default CompoundTag transformTileData(BlockState state, CompoundTag tileData, MMTransform transform) {
        return tileData;
    }

    /**
     * Adjusts orientation a vanilla {@code rotate/mirror} and per-property facing rewrite can't express,
     * once the rest of the block state has already been transformed. {@code original} is the captured
     * pre-transform state; {@code rotated} is the state so far. Used for compound orientations such as a
     * GregTech multiblock's upwards facing (a relative roll that depends on the front facing). Runs at
     * build generation, so it shows in the ghost preview too. Default no-op; return the new state.
     */
    default BlockState transformBlockState(BlockState rotated, BlockState original, MMTransform transform) {
        return rotated;
    }

    /**
     * Runs after the whitelisted tag has been loaded into the placed block entity. Used to
     * re-apply settings through mod APIs and to charge the player for covers, parts, and any
     * single-slot items the handler kept.
     */
    default void afterPaste(ServerLevel level, BlockPos pos, BlockEntity be,
                            CompoundTag full, MMInventory inv) {}
}
