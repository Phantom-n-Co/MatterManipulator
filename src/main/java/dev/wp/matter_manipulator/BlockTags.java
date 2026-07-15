package dev.wp.matter_manipulator;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class BlockTags {
    public static final TagKey<Block> DROP_BLACKLISTED = tag("drop_blacklisted");
    public static final TagKey<Block> ITEMLESS_BLOCK = tag("itemless_block");

    private static TagKey<Block> tag(String name) {
        return TagKey.create(Registries.BLOCK, MMMod.id(name));
    }
}
