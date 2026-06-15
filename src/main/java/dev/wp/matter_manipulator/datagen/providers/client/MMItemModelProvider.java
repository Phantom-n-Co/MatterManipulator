package dev.wp.matter_manipulator.datagen.providers.client;

import dev.wp.matter_manipulator.MMMod;
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
        handheldItem(MMItems.MANIPULATOR_PROTO);
        handheldItem(MMItems.MANIPULATOR_MK1);
        handheldItem(MMItems.MANIPULATOR_MK2);
        handheldItem(MMItems.MANIPULATOR_MK3);
    }

    private void handheldItem(RegistryObject<?> item) {
        withExistingParent(item.getId().getPath(), "item/handheld")
                .texture("layer0", MMMod.MODID + ":item/" + item.getId().getPath());
    }
}
