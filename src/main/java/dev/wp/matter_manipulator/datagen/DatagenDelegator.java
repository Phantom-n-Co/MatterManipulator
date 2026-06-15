package dev.wp.matter_manipulator.datagen;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.datagen.providers.DatagenDelegatorClient;
import dev.wp.matter_manipulator.datagen.providers.DatagenDelegatorServer;

import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DatagenDelegator {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DatagenDelegatorClient.configure(event);
        DatagenDelegatorServer.configure(event);
    }
}
