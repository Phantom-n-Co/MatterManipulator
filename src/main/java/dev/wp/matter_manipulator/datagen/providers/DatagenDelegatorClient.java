package dev.wp.matter_manipulator.datagen.providers;

import dev.wp.matter_manipulator.datagen.providers.client.MMItemModelProvider;
import dev.wp.matter_manipulator.datagen.providers.client.MMLanguageProvider;
import java.util.function.Function;
import net.minecraft.data.DataProvider;
import net.minecraftforge.data.event.GatherDataEvent;

public class DatagenDelegatorClient {
    public static void configure(GatherDataEvent event) {
        add(event, MMItemModelProvider::new);
        add(event, MMLanguageProvider::new);
    }

    private static void add(GatherDataEvent event, Function<GatherDataEvent, DataProvider> provider) {
        event.getGenerator().addProvider(event.includeClient(), provider.apply(event));
    }
}
