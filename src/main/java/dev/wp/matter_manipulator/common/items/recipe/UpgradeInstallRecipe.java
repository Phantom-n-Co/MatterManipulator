package dev.wp.matter_manipulator.common.items.recipe;

import dev.wp.matter_manipulator.common.items.MMRecipes;
import dev.wp.matter_manipulator.common.items.MMUpgrades;
import dev.wp.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import dev.wp.matter_manipulator.common.items.manipulator.MMState;
import java.util.BitSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Custom crafting recipe that installs Matter Manipulator upgrade items onto a manipulator,
 * preserving its NBT (config, energy, existing upgrades). Several upgrades can be installed in one
 * craft. Install-only — there is no recipe to remove an upgrade.
 */
public class UpgradeInstallRecipe extends CustomRecipe {

    public UpgradeInstallRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        ItemStack manipulator = ItemStack.EMPTY;
        boolean hasUpgrade = false;
        boolean hasNewUpgrade = false;
        BitSet installed = null;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof ItemMatterManipulator) {
                if (!manipulator.isEmpty()) return false; // only one manipulator allowed
                manipulator = s;
                installed = MMState.of(s).getUpgrades();
            } else if (MMUpgrades.fromItem(s.getItem()) == null) {
                return false; // a non-upgrade, non-manipulator item — not our recipe
            }
        }
        if (manipulator.isEmpty()) return false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() instanceof ItemMatterManipulator) continue;
            MMUpgrades up = MMUpgrades.fromItem(s.getItem());
            if (up == null) return false;
            hasUpgrade = true;
            if (!installed.get(up.bit)) hasNewUpgrade = true;
        }
        return hasUpgrade && hasNewUpgrade;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        ItemStack manipulator = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof ItemMatterManipulator) {
                manipulator = s;
                break;
            }
        }
        if (manipulator.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = manipulator.copy();
        result.setCount(1);
        MMState state = MMState.of(result);
        BitSet bits = state.getUpgrades();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s.getItem() instanceof ItemMatterManipulator) continue;
            MMUpgrades up = MMUpgrades.fromItem(s.getItem());
            if (up != null) bits.set(up.bit);
        }
        state.setUpgrades(bits);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MMRecipes.UPGRADE_INSTALL.get();
    }
}
