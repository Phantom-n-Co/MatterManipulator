package dev.wp.matter_manipulator.common.building.analysis;

import dev.wp.matter_manipulator.common.items.manipulator.MMTransform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/**
 * Vanilla block-entity configuration with no item-handler path: custom names, container locks, sign
 * text, banner patterns, plus the single chargeable item slots of jukeboxes and lecterns. The free
 * config is loaded wholesale; the record/book are installed only if the player can pay. Replaces the
 * old {@code VanillaMiscHandler}. Container <em>contents</em> are handled by {@link InventoryAnalysis}.
 */
public class VanillaMiscAnalysis implements ITileAnalysis {
    /** Config keys with no item cost — copied verbatim when present. */
    private static final String[] CONFIG_KEYS = {
            "CustomName", "Lock", "front_text", "back_text", "is_waxed", "Patterns",
    };

    private CompoundTag config;
    @Nullable
    private CompoundTag record;
    @Nullable
    private CompoundTag book;
    @Nullable
    private Tag page;

    /** Captures vanilla config/items, or null if the block entity has none of them. */
    public static VanillaMiscAnalysis analyze(BlockEntity be) {
        CompoundTag full = be.saveWithFullMetadata();
        CompoundTag config = new CompoundTag();
        if (full.contains("id")) config.putString("id", full.getString("id"));
        boolean any = false;
        for (String key : CONFIG_KEYS) {
            if (full.contains(key)) {
                config.put(key, full.get(key).copy());
                any = true;
            }
        }
        VanillaMiscAnalysis a = new VanillaMiscAnalysis();
        a.config = config;
        if (full.contains("RecordItem", Tag.TAG_COMPOUND)) {
            a.record = full.getCompound("RecordItem").copy();
            any = true;
        }
        if (full.contains("Book", Tag.TAG_COMPOUND)) {
            a.book = full.getCompound("Book").copy();
            if (full.contains("Page")) a.page = full.get("Page").copy();
            any = true;
        }
        return any ? a : null;
    }

    @Override
    public boolean apply(BlockApplyContext ctx) {
        BlockEntity be = ctx.getBlockEntity();
        if (be == null) return true;
        CompoundTag tag = config.copy();
        if (record != null) {
            ItemStack stack = ItemStack.of(record);
            if (!stack.isEmpty() && ctx.tryConsume(stack)) tag.put("RecordItem", record.copy());
        }
        if (book != null) {
            ItemStack stack = ItemStack.of(book);
            if (!stack.isEmpty() && ctx.tryConsume(stack)) {
                tag.put("Book", book.copy());
                if (page != null) tag.put("Page", page.copy());
            }
        }
        be.load(tag);
        be.setChanged();
        return true;
    }

    @Override
    public void collectRequiredItems(RequiredItems out) {
        if (record != null) out.add(ItemStack.of(record));
        if (book != null) out.add(ItemStack.of(book));
    }

    @Override
    public void transform(MMTransform transform) {}

    @Override
    public ITileAnalysis copy() {
        VanillaMiscAnalysis dup = new VanillaMiscAnalysis();
        dup.config = config.copy();
        dup.record = record == null ? null : record.copy();
        dup.book = book == null ? null : book.copy();
        dup.page = page == null ? null : page.copy();
        return dup;
    }

    @Override
    public void contributePreviewNbt(CompoundTag out) {
        for (String key : config.getAllKeys()) {
            out.put(key, config.get(key).copy());
        }
    }
}
