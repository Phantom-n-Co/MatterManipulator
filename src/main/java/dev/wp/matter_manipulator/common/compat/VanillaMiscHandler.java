package dev.wp.matter_manipulator.common.compat;

import dev.wp.matter_manipulator.common.building.MMInventory;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whitelists vanilla block-entity configuration that has no item-handler capability path, so it
 * survives a COPY: custom names, container locks, sign text, banner patterns, and the single-item
 * slots of jukeboxes and lecterns. Container <em>contents</em> are not handled here — they are
 * restored generically through the item-handler capability by {@code PendingBuild.injectContents}.
 */
public class VanillaMiscHandler implements CopyHandler {

    /** Config keys with no item cost — copied verbatim when present. */
    private static final String[] CONFIG_KEYS = {
        "CustomName", "Lock", "front_text", "back_text", "is_waxed", "Patterns",
    };

    @Override
    public boolean matches(BlockState state, @Nullable BlockEntity be, @Nullable CompoundTag fullTag) {
        return true;
    }

    @Override
    public void contributeNbt(CompoundTag full, CompoundTag out, MMInventory inv) {
        for (String key : CONFIG_KEYS) {
            if (full.contains(key)) out.put(key, full.get(key).copy());
        }
        // Jukebox record and lectern book are real items: keep them only if the player can pay.
        keepChargeableItem(full, out, inv, "RecordItem");
        keepChargeableItem(full, out, inv, "Book", "Page");
    }

    private static void keepChargeableItem(
            CompoundTag full, CompoundTag out, MMInventory inv, String itemKey, String... companionKeys) {
        if (!full.contains(itemKey, Tag.TAG_COMPOUND)) return;
        ItemStack stack = ItemStack.of(full.getCompound(itemKey));
        if (stack.isEmpty()) return;
        if (!inv.getPlayer().isCreative() && !inv.canConsume(stack)) return;
        inv.consume(stack);
        out.put(itemKey, full.get(itemKey).copy());
        for (String companion : companionKeys) {
            if (full.contains(companion)) out.put(companion, full.get(companion).copy());
        }
    }
}
