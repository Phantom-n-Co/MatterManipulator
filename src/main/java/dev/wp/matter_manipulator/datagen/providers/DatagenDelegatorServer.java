package dev.wp.matter_manipulator.datagen.providers;

import dev.wp.matter_manipulator.datagen.providers.server.MMBlockTagsProvider;
import dev.wp.matter_manipulator.datagen.providers.server.MMLootTableProvider;
import dev.wp.matter_manipulator.datagen.providers.server.MMRecipeProvider;

import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Function;

public class DatagenDelegatorServer {
    public static void configure(GatherDataEvent event) {
        add(event, MMBlockTagsProvider::new);
        add(event, MMLootTableProvider::new);
        add(event, MMRecipeProvider::new);
    }

    private static void add(GatherDataEvent event, Function<GatherDataEvent, DataProvider> provider) {
        event.getGenerator().addProvider(event.includeServer(), provider.apply(event));
    }
}
