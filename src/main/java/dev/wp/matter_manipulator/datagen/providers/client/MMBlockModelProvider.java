package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.MMMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.BlockModelProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public class MMBlockModelProvider extends BlockModelProvider {
    public MMBlockModelProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), MMMod.MODID, event.getExistingFileHelper());
    }

    @Override
    protected void registerModels() {
        cubeAll("ae2_uplink", ResourceLocation.fromNamespaceAndPath(MMMod.MODID, "block/ae2_uplink"));
    }
}
