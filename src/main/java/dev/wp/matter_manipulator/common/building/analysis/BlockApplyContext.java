package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.building.Location;
import dev.wp.matter_manipulator.common.building.MMInventory;
import dev.wp.matter_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * The environment a {@link ITileAnalysis#apply} runs against: the placed block entity plus the item
 * source ({@link MMInventory}, which spans the player inventory and any bound AE2 uplink). It unifies
 * the consume/give/message operations that were previously scattered across {@code PendingBuild} and
 * the per-mod copy handlers, so every analysis charges the player the same way.
 *
 * <p>This is the build-time (apply) context. The required-items preview uses
 * {@link ITileAnalysis#collectRequiredItems(RequiredItems)} instead, which tallies from the
 * analysis's own data and never touches the world.
 */
public class BlockApplyContext {
    private final ServerLevel level;
    private final BlockPos pos;
    private final MMInventory inv;
    @Nullable
    private final Map<Long, Long> wirelessRemap;

    public BlockApplyContext(ServerLevel level, BlockPos pos, MMInventory inv) {
        this(level, pos, inv, null);
    }

    public BlockApplyContext(ServerLevel level, BlockPos pos, MMInventory inv, @Nullable Map<Long, Long> wirelessRemap) {
        this.level = level;
        this.pos = pos;
        this.inv = inv;
        this.wirelessRemap = wirelessRemap;
    }

    /**
     * Build-scoped original→fresh frequency remap for ExtendedAE wireless re-linking, shared across
     * every block in one build so a copied connector pair settles on one new frequency. Null outside a build.
     */
    @Nullable
    public Map<Long, Long> wirelessRemap() {
        return wirelessRemap;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public MMInventory inv() {
        return inv;
    }

    public Location uplink() {
        return inv.getUplink();
    }

    public Player player() {
        return inv.getPlayer();
    }

    public boolean isCreative() {
        return inv.getPlayer().isCreative();
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        return level.getBlockEntity(pos);
    }

    /** True if the player can supply {@code stack} (inventory + bound network), or is in creative. */
    public boolean canConsume(ItemStack stack) {
        return inv.canConsume(stack);
    }

    /**
     * Charges {@code stack} to the player if they can pay (or are creative); returns whether it was
     * paid. Nothing is consumed when it returns false, so callers can gate placement on the result.
     */
    public boolean tryConsume(ItemStack stack) {
        if (stack.isEmpty() || isCreative()) return true;
        if (!inv.canConsume(stack)) return false;
        inv.consume(stack);
        return true;
    }

    /** Returns an item to the player (network → inventory → dropped). Used only for genuinely owned items. */
    public void give(ItemStack stack) {
        inv.give(stack);
    }

    public void warn(Component message) {
        MMUtils.sendWarningToPlayer(player(), message);
    }

    public void error(Component message) {
        MMUtils.sendErrorToPlayer(player(), message);
    }
}
