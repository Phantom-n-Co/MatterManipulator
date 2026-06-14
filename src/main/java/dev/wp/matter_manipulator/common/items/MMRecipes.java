package dev.wp.matter_manipulator.common.items;

import dev.wp.matter_manipulator.MMMod;
import dev.wp.matter_manipulator.common.items.recipe.UpgradeInstallRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Recipe serializers added by the mod. */
public class MMRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MMMod.MODID);

    public static final RegistryObject<SimpleCraftingRecipeSerializer<UpgradeInstallRecipe>> UPGRADE_INSTALL =
            SERIALIZERS.register("upgrade_install",
                    () -> new SimpleCraftingRecipeSerializer<>(UpgradeInstallRecipe::new));
}
