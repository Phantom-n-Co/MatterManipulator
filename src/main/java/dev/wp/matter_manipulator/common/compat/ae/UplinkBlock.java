package dev.wp.matter_manipulator.common.compat.ae;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Matter Manipulator AE2 uplink block: a network bridge the manipulator binds to via this
 * block's GUI (drop a manipulator into the link slot).
 */
public class UplinkBlock extends AEBaseEntityBlock<UplinkBlockEntity> {

    public UplinkBlock() {
        super(BlockBehaviour.Properties.of().strength(2.5f));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UplinkBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(pos) instanceof UplinkBlockEntity be)) {
            return InteractionResult.FAIL;
        }
        NetworkHooks.openScreen(
                sp,
                new SimpleMenuProvider(
                        (id, inv, p) -> new UplinkMenu(id, inv, be),
                        Component.translatable("block.matter_manipulator.ae2_uplink")),
                buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }
}
