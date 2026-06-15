package dev.wp.matter_manipulator;

import dev.wp.matter_manipulator.common.compat.GTCompat;
import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;
import dev.wp.matter_manipulator.common.config.MMModConfig;
import dev.wp.matter_manipulator.common.items.MMItems;
import dev.wp.matter_manipulator.common.items.MMRecipes;
import dev.wp.matter_manipulator.common.networking.MMNetwork;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod(MMMod.MODID)
public class MMMod {
    public static final String MODID = "matter_manipulator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MMMod(FMLJavaModLoadingContext ctx) {
        IEventBus modBus = ctx.getModEventBus();

        MMItems.ITEMS.register(modBus);
        MMItems.CREATIVE_MODE_TABS.register(modBus);
        MMRecipes.SERIALIZERS.register(modBus);

        // AE2 uplink block — registered only when AE2 is present, so its AE2-coupled classes
        // (block/BE/logic) never load otherwise.
        if (Mods.AE2) {
            AEUplinkRegistrar.register(modBus);
        }

        modBus.addListener(this::commonSetup);

        ctx.registerConfig(ModConfig.Type.COMMON, MMModConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MMNetwork::init);
        // GregTech-only Forge event handlers; gated so GTCompat never loads without GregTech.
        if (Mods.GTCEU) {
            event.enqueueWork(GTCompat::registerEvents);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(MODID + ":" + path);
    }
}
