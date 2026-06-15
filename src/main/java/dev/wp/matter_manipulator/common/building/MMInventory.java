package dev.wp.matter_manipulator.common.building;

import dev.wp.matter_manipulator.common.compat.AECompat;
import dev.wp.matter_manipulator.common.utils.Mods;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Manages item consumption for building operations. Sources from the player's inventory and,
 * when the manipulator is bound to an AE2 uplink, from that network (storage + uplink buffer).
 */
public class MMInventory {

    private final Player player;
    /** Bound AE2 uplink (null if unbound); resolved through {@link AECompat} so this class stays AE2-free. */
    @Nullable
    private final Location uplink;
    /** Server level the player is in, used for uplink lookups; null on the client. */
    @Nullable
    private final ServerLevel level;

    public MMInventory(Player player) {
        this(player, null);
    }

    public MMInventory(Player player, @Nullable Location uplink) {
        this.player = player;
        this.uplink = uplink;
        this.level = player.level() instanceof ServerLevel sl ? sl : null;
    }

    private boolean uplinkActive() {
        return Mods.AE2 && uplink != null && level != null;
    }

    /**
     * Checks whether the required items are available across the player's inventory and the
     * bound network.
     */
    public boolean canConsume(ItemStack required) {
        if (required.isEmpty()) return true;
        if (player.isCreative()) return true;
        long have = countInInventory(required);
        if (have >= required.getCount()) return true;
        if (uplinkActive()) {
            have += AECompat.uplinkAvailable(level, uplink, required);
        }
        return have >= required.getCount();
    }

    /**
     * Takes the required item from the player's inventory first, then from the bound network.
     * Does nothing in creative mode.
     */
    public void consume(ItemStack required) {
        if (required.isEmpty() || player.isCreative()) return;
        int remaining = required.getCount() - takeFromInventory(required, required.getCount());
        if (remaining > 0 && uplinkActive()) {
            remaining -= (int) AECompat.uplinkExtract(level, uplink, required, remaining);
        }
    }

    /** Removes up to {@code max} of {@code required} from the player inventory; returns the amount taken. */
    private int takeFromInventory(ItemStack required, int max) {
        int remaining = max;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            var stack = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(stack, required)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return max - remaining;
    }

    /**
     * Gives an item back: into the bound network first, then the player's inventory, then dropped
     * at their feet.
     */
    public void give(ItemStack stack) {
        if (stack.isEmpty() || player.isCreative()) return;
        ItemStack remaining = stack;
        if (uplinkActive()) {
            int left = AECompat.uplinkInsert(level, uplink, stack);
            if (left <= 0) return;
            remaining = stack.copyWithCount(left);
        }
        if (!player.getInventory().add(remaining)) {
            player.drop(remaining, false);
        }
    }

    public int countInInventory(ItemStack target) {
        int total = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(stack, target)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Total available across the player's inventory and the bound network (for planning). */
    public long countAvailable(ItemStack target) {
        long total = countInInventory(target);
        if (uplinkActive()) {
            total += AECompat.uplinkAvailable(level, uplink, target);
        }
        return total;
    }

    public Player getPlayer() {
        return player;
    }
}
