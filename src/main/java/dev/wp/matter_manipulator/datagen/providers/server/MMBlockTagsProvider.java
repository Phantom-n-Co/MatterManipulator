package dev.wp.matter_manipulator.datagen.providers.server;

import dev.wp.matter_manipulator.BlockTags;
import dev.wp.matter_manipulator.MMMod;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public class MMBlockTagsProvider extends BlockTagsProvider {
    public MMBlockTagsProvider(GatherDataEvent event) {
        super(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                MMMod.MODID,
                event.getExistingFileHelper());
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.DROP_BLACKLISTED).addTags(Tags.Blocks.ORES);
    }
}
