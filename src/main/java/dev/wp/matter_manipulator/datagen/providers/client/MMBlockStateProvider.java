package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;

import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public class MMBlockStateProvider extends BlockStateProvider {
    public MMBlockStateProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), MMMod.MODID, event.getExistingFileHelper());
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(AEUplinkRegistrar.UPLINK_BLOCK.get());
    }
}
