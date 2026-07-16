package dev.wp.matter_manipulator.datagen;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.datagen.providers.client.MMBlockModelProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMBlockStateProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMItemModelProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMLanguageProvider;
import dev.wp.matter_manipulator.datagen.providers.server.MMBlockTagsProvider;
import dev.wp.matter_manipulator.datagen.providers.server.MMLootTableProvider;
import dev.wp.matter_manipulator.datagen.providers.server.MMRecipeProvider;

import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MMDataGen {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(), new MMBlockModelProvider(event));
        event.getGenerator().addProvider(event.includeClient(), new MMBlockStateProvider(event));
        event.getGenerator().addProvider(event.includeClient(), new MMItemModelProvider(event));
        event.getGenerator().addProvider(event.includeClient(), new MMLanguageProvider(event));
        event.getGenerator().addProvider(event.includeServer(), new MMBlockTagsProvider(event));
        event.getGenerator().addProvider(event.includeServer(), new MMLootTableProvider(event));
        event.getGenerator().addProvider(event.includeServer(), new MMRecipeProvider(event));
    }
}
