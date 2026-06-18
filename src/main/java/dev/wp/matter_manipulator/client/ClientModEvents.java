package dev.wp.matter_manipulator.client;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;
import dev.wp.matter_manipulator.common.compat.ae.UplinkScreen;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MMMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // AE2 uplink GUI — registered only when AE2 is present.
        if (Mods.AE2) {
            event.enqueueWork(() -> MenuScreens.register(AEUplinkRegistrar.UPLINK_MENU.get(), UplinkScreen::new));
        }
    }
}
