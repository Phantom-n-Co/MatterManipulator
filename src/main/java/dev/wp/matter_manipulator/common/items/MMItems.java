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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MMItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MMMod.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MMMod.MODID);

    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_PROTO = ITEMS.register("manipulator_proto", () -> new ItemMatterManipulator(MMTier.PROTO));
    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK1 = ITEMS.register("manipulator_mk1", () -> new ItemMatterManipulator(MMTier.MK1));
    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK2 = ITEMS.register("manipulator_mk2", () -> new ItemMatterManipulator(MMTier.MK2));
    public static final RegistryObject<ItemMatterManipulator> MANIPULATOR_MK3 = ITEMS.register("manipulator_mk3", () -> new ItemMatterManipulator(MMTier.MK3));

    public static final RegistryObject<Item> UPGRADE_BLANK = basicItem("upgrade_blank");
    public static final RegistryObject<Item> UPGRADE_POWER_P2P = basicItem("upgrade_power_p2p");
    public static final RegistryObject<Item> UPGRADE_MINING = basicItem("upgrade_mining");
    public static final RegistryObject<Item> UPGRADE_SPEED = basicItem("upgrade_speed");
    public static final RegistryObject<Item> UPGRADE_POWER_EFF = basicItem("upgrade_power_eff");

    static {
        for (var tier : MMTier.values()) {
            String mark = tier.ordinal() == 0 ? "proto_" : "mk" + tier.ordinal() + "_";
            mark = "part_" + mark;
            basicItem(mark + "frame");
            basicItem(mark + "lens");
            basicItem(mark + "computer_core");
            basicItem(mark + "power_core");
            basicItem(mark + "teleporter_core");
        }
    }

    public static final RegistryObject<CreativeModeTab> MATTER_MANIPULATOR_TAB = CREATIVE_MODE_TABS.register("matter_manipulator_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.matter_manipulator"))
            .icon(() -> new ItemStack(MANIPULATOR_PROTO.get()))
            .displayItems((params, output) -> {
                List<Item> toDisplay = new ArrayList<>();
                for (var item : ITEMS.getEntries()) toDisplay.add(item.get());
                if (Mods.AE2) toDisplay.add(AEUplinkRegistrar.UPLINK_ITEM.get());

                for (var item : toDisplay) output.accept(item);
            })
            .build());

    private static RegistryObject<Item> basicItem(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
