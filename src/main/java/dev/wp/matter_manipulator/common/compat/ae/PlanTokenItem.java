package dev.wp.matter_manipulator.common.compat.ae;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The synthetic output of an uplink plan pattern. Requesting it from an AE terminal makes the
 * network craft a build's blocks (the pattern's inputs) into ME storage. Each plan gets a unique
 * id so distinct plans are distinct items; the display name reflects the plan's block count.
 */
public class PlanTokenItem extends Item {

    public PlanTokenItem() {
        super(new Item.Properties());
    }

    public static ItemStack create(UUID owner, long id, long totalBlocks) {
        ItemStack stack = new ItemStack(AEUplinkRegistrar.PLAN_TOKEN.get());
        var tag = stack.getOrCreateTag();
        tag.putUUID("owner", owner);
        tag.putLong("id", id);
        tag.putLong("blocks", totalBlocks);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null && tag.contains("blocks")) {
            return Component.translatable("item.matter_manipulator.plan_token.named", tag.getLong("blocks"));
        }
        return super.getName(stack);
    }
}
