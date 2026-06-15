package dev.wp.matter_manipulator.common.items;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMTier;
import dev.wp.matter_manipulator.common.utils.Mods;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MMItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MMMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MMMod.MODID);

    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_PROTO = ITEMS.register("manipulator_proto", () -> new ItemMatterManipulator(MMTier.PROTO));

    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK1 = ITEMS.register("manipulator_mk1", () -> new ItemMatterManipulator(MMTier.MK1));

    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK2 = ITEMS.register("manipulator_mk2", () -> new ItemMatterManipulator(MMTier.MK2));

    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK3 = ITEMS.register("manipulator_mk3", () -> new ItemMatterManipulator(MMTier.MK3));

    public static final RegistryObject<Item> UPGRADE_POWER_P2P = ITEMS.register("upgrade_power_p2p", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UPGRADE_MINING = ITEMS.register("upgrade_mining", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UPGRADE_SPEED = ITEMS.register("upgrade_speed", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> UPGRADE_POWER_EFF = ITEMS.register("upgrade_power_eff", () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> MATTER_MANIPULATOR_TAB = CREATIVE_MODE_TABS.register("matter_manipulator_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.matter_manipulator"))
            .icon(() -> new ItemStack(MANIPULATOR_PROTO.get()))
            .displayItems((params, output) -> {
                output.accept(MANIPULATOR_PROTO.get());
                output.accept(MANIPULATOR_MK1.get());
                output.accept(MANIPULATOR_MK2.get());
                output.accept(MANIPULATOR_MK3.get());
                output.accept(UPGRADE_POWER_P2P.get());
                output.accept(UPGRADE_MINING.get());
                output.accept(UPGRADE_SPEED.get());
                output.accept(UPGRADE_POWER_EFF.get());
                if (Mods.AE2) {
                    AEUplinkRegistrar.addToCreativeTab(output);
                }
            })
            .build());
}
