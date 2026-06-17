package dev.wp.matter_manipulator.datagen.providers.server;

import dev.wp.matter_manipulator.common.compat.ae.AEUplinkRegistrar;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.data.event.GatherDataEvent;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class MMLootTableProvider extends LootTableProvider {
    public MMLootTableProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), Collections.emptySet(), Collections.singletonList(new LootTableProvider.SubProviderEntry(SubProvider::new, LootContextParamSets.BLOCK)));
    }

    public static class SubProvider extends BlockLootSubProvider {
        protected SubProvider() {
            super(Collections.emptySet(), FeatureFlagSet.of(), new HashMap<>());
        }

        @Override
        protected void generate() {
            add(AEUplinkRegistrar.UPLINK_BLOCK.get(), createSingleItemTable(AEUplinkRegistrar.UPLINK_ITEM.get()));
        }

        public void generate(@NotNull BiConsumer<ResourceLocation, LootTable.Builder> bi) {
            this.generate();
            for (var e : this.map.entrySet()) {
                bi.accept(e.getKey(), e.getValue());
            }
        }
    }
}
