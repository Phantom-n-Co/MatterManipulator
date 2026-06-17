package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;
import dev.wp.matter_manipulator.common.items.MMItems;

import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.registries.RegistryObject;

public class MMItemModelProvider extends ItemModelProvider {
    public MMItemModelProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), MMMod.MODID, event.getExistingFileHelper());
    }

    @Override
    protected void registerModels() {
        this.blocks();
        this.items();
    }

    private void handheldItem(RegistryObject<?> item) {
        if (item.getId() == null) {
            LOGGER.error("Item model for {} is missing registry name", item.get());
            return;
        }
        this.withExistingParent(item.getId().getPath(), "item/handheld")
                .texture("layer0", MMMod.MODID + ":item/" + item.getId().getPath());
    }

    private void items() {
        for (var name : MMItems.ITEMS.getEntries()) {
            if (name.getId() == null) {
                LOGGER.error("Item model for {} is missing registry name", name.get());
                continue;
            }

            if (name.getId().getPath().startsWith("upgrade")) {
                this.withExistingParent(name.getId().getPath(), "item/generated")
                        .texture("layer0", modLoc("item/upgrade/" + name.getId().getPath().replace("upgrade_", "")));
                continue;
            }

            if (name.getId().getPath().startsWith("part_")) {
                this.withExistingParent(name.getId().getPath(), "item/generated")
                        .texture("layer0", modLoc("item/part/" + name.getId().getPath().replace("part_", "")));
                continue;
            }

            if (name.getId().getPath().contains("manipulator")) {
                handheldItem(name);
                continue;
            }

            basicItem(name.get());
        }

        if (AEUplinkRegistrar.UPLINK_ITEM.getId() != null)
            this.withExistingParent(AEUplinkRegistrar.UPLINK_ITEM.getId().getPath(), modLoc("block/ae2_uplink"));
        if (AEUplinkRegistrar.PLAN_TOKEN.getId() != null)
            basicItem(AEUplinkRegistrar.PLAN_TOKEN.getId());
    }

    private void blocks() {
        for (var name : AEUplinkRegistrar.BLOCKS.getEntries()) {
            if (name.getId() == null) {
                LOGGER.error("Block model for {} is missing registry name", name.get());
                continue;
            }
            this.withExistingParent(name.getId().getPath(), this.modLoc("block/" + name.getId().getPath()));
        }
    }
}
