package dev.wp.matter_manipulator.datagen.providers.server;

import dev.wp.matter_manipulator.common.items.MMRecipes;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraftforge.data.event.GatherDataEvent;

public class MMRecipeProvider extends RecipeProvider {

    public MMRecipeProvider(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput());
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        SpecialRecipeBuilder.special(MMRecipes.UPGRADE_INSTALL.get())
                .save(writer, "matter_manipulator:upgrade_install");
    }
}
