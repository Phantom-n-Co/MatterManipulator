package dev.wp.matter_manipulator.datagen.providers.server;

import dev.wp.matter_manipulator.BlockTags;
import dev.wp.matter_manipulator.MMMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.data.event.GatherDataEvent;

import org.jetbrains.annotations.NotNull;

public class MMBlockTagsProvider extends BlockTagsProvider {
    public MMBlockTagsProvider(GatherDataEvent event) {
        super(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                MMMod.MODID,
                event.getExistingFileHelper());
    }

    @Override
    protected void addTags(@NotNull HolderLookup.Provider provider) {
        this.tag(BlockTags.DROP_BLACKLISTED).addTag(Tags.Blocks.ORES);
        this.tag(BlockTags.ITEMLESS_BLOCK).addOptional(ResourceLocation.parse("ae2:cable_bus"));
    }

    @Override
    @NotNull
    public String getName() {
        return this.getClass().getCanonicalName();
    }
}
