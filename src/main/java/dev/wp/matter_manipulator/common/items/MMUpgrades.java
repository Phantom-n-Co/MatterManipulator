package dev.wp.matter_manipulator.common.items;

import dev.wp.matter_manipulator.common.items.manipulator.MMCapability;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public enum MMUpgrades {
    POWER_P2P(0, 0, MMItems.UPGRADE_POWER_P2P),
    MINING(1, MMCapability.ALLOW_REMOVING, MMItems.UPGRADE_MINING),
    SPEED(2, 0, MMItems.UPGRADE_SPEED),
    POWER_EFF(3, 0, MMItems.UPGRADE_POWER_EFF);

    public final int bit;
    public final int providesCaps;
    public final RegistryObject<Item> item;

    MMUpgrades(int bit, int providesCaps, RegistryObject<Item> item) {
        this.bit = bit;
        this.providesCaps = providesCaps;
        this.item = item;
    }

    private static final Map<Item, MMUpgrades> BY_ITEM = new HashMap<>();

    static {
        for (MMUpgrades u : values()) {
            BY_ITEM.put(u.item.get(), u);
        }
    }

    public static MMUpgrades fromItem(Item item) {
        return BY_ITEM.get(item);
    }
}
