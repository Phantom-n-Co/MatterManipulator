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
        handheldItem(MMItems.MANIPULATOR_PROTO);
        handheldItem(MMItems.MANIPULATOR_MK1);
        handheldItem(MMItems.MANIPULATOR_MK2);
        handheldItem(MMItems.MANIPULATOR_MK3);
        basicItem(AEUplinkRegistrar.PLAN_TOKEN.get());
    }

    private void handheldItem(RegistryObject<?> item) {
        if (item.getId() != null)
            this.withExistingParent(item.getId().getPath(), "item/handheld")
                    .texture("layer0", MMMod.MODID + ":item/" + item.getId().getPath());
        else LOGGER.error("Item model for {} is missing registry name", item.get());
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
