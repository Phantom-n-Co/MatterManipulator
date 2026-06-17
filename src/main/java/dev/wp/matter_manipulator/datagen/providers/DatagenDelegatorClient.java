package dev.wp.matter_manipulator.datagen.providers;

import dev.wp.matter_manipulator.datagen.providers.client.MMBlockModelProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMBlockStateProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMItemModelProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMLanguageProvider;

import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Function;

public class DatagenDelegatorClient {
    public static void configure(GatherDataEvent event) {
        add(event, MMBlockModelProvider::new);
        add(event, MMBlockStateProvider::new);
        add(event, MMItemModelProvider::new);
        add(event, MMLanguageProvider::new);
    }

    private static void add(GatherDataEvent event, Function<GatherDataEvent, DataProvider> provider) {
        event.getGenerator().addProvider(event.includeClient(), provider.apply(event));
    }
}
