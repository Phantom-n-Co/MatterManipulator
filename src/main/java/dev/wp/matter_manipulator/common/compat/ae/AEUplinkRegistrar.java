package dev.wp.matter_manipulator.common.compat.ae;

import dev.wp.matter_manipulator.MMMod;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;

/**
 * Registration for the AE2 uplink block. Lives in an AE2-only class and is hooked to the mod bus
 * from {@link MMMod} <b>only when AE2 is present</b>, so none of the AE2-coupled classes load
 * without the mod. Mirrors SpatialToolsCompatible's {@code AE2BlockRegistrar}.
 */
public final class AEUplinkRegistrar {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MMMod.MODID);

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MMMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MMMod.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MMMod.MODID);

    public static final RegistryObject<UplinkBlock> UPLINK_BLOCK = BLOCKS.register("ae2_uplink", UplinkBlock::new);

    public static final RegistryObject<Item> UPLINK_ITEM = ITEMS.register("ae2_uplink", () -> new BlockItem(UPLINK_BLOCK.get(), new Item.Properties()));

    /** Synthetic output of plan patterns; not in the creative tab (internal token). */
    public static final RegistryObject<PlanTokenItem> PLAN_TOKEN = ITEMS.register("plan_token", PlanTokenItem::new);

    public static final RegistryObject<BlockEntityType<UplinkBlockEntity>> UPLINK_BE_TYPE = reg("ae2_uplink", UPLINK_BLOCK, UplinkBlockEntity::new, UplinkBlockEntity.class, (BlockEntityTicker<UplinkBlockEntity>) (lvl, pos, st, be) -> be.serverTick());

    public static final RegistryObject<MenuType<UplinkMenu>> UPLINK_MENU = MENU_TYPES.register("ae2_uplink", () -> IForgeMenuType.create(UplinkMenu::new));

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends AEBaseBlockEntity> RegistryObject<BlockEntityType<T>> reg(
            String id,
            RegistryObject<? extends AEBaseEntityBlock<?>> block,
            BlockEntityType.BlockEntitySupplier<T> factory,
            Class<T> beClass,
            BlockEntityTicker<T> serverTicker) {
        return BLOCK_ENTITY_TYPES.register(id, () -> {
            var blk = block.get();
            var type = BlockEntityType.Builder.of(factory, blk).build(null);
            blk.setBlockEntity((Class) beClass, (BlockEntityType) type, null, (BlockEntityTicker) serverTicker);
            return type;
        });
    }

    /** Hook all registers to the mod event bus. Call only when AE2 is loaded. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
    }

    /** Append the uplink item to the mod creative tab. Call only when AE2 is loaded. */
    public static void addToCreativeTab(CreativeModeTab.Output output) {
        output.accept(UPLINK_ITEM.get());
    }

    private AEUplinkRegistrar() {}
}
