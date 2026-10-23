package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

/** Merchant list that respects the selected offer and distinguishes metadata and NBT. */
public final class ExactMerchantRecipeList extends MerchantRecipeList {

    @Override
    public MerchantRecipe canRecipeBeUsed(ItemStack primary, ItemStack secondary, int recipeIndex) {
        if (recipeIndex >= 0 && recipeIndex < size()) {
            MerchantRecipe selected = (MerchantRecipe) get(recipeIndex);
            return matches(selected, primary, secondary) ? selected : null;
        }

        for (Object value : this) {
            MerchantRecipe recipe = (MerchantRecipe) value;
            if (matches(recipe, primary, secondary)) return recipe;
        }
        return null;
    }

    private boolean matches(MerchantRecipe recipe, ItemStack primary, ItemStack secondary) {
        if (!matchesStack(recipe.getItemToBuy(), primary)) return false;
        if (!recipe.hasSecondItemToBuy()) return secondary == null;
        return matchesStack(recipe.getSecondItemToBuy(), secondary);
    }

    private boolean matchesStack(ItemStack required, ItemStack supplied) {
        return required != null && supplied != null
            && required.getItem() == supplied.getItem()
            && required.getItemDamage() == supplied.getItemDamage()
            && supplied.stackSize >= required.stackSize
            && ItemStack.areItemStackTagsEqual(required, supplied);
    }
}
