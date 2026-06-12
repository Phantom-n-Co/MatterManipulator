package dev.wp.matter_manipulator.common.building;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Manages item consumption for building operations.
 * Currently, sources are only from player inventory; AE2/Uplink hooks are added later.
 */
public class MMInventory {

    private final Player player;

    public MMInventory(Player player) {
        this.player = player;
    }

    /**
     * Checks whether the player has the required items.
     * @return true if all items are available
     */
    public boolean canConsume(ItemStack required) {
        if (required.isEmpty()) return true;
        if (player.isCreative()) return true;
        return countInInventory(required) >= required.getCount();
    }

    /**
     * Takes one copy of the required item from the player's inventory.
     * Does nothing in creative mode.
     */
    public void consume(ItemStack required) {
        if (required.isEmpty() || player.isCreative()) return;
        int remaining = required.getCount();
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
    }

    /**
     * Gives an item to the player. If the inventory is full, drops it at their feet.
     */
    public void give(ItemStack stack) {
        if (stack.isEmpty() || player.isCreative()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
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

    public Player getPlayer() {
        return player;
    }
}
