package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.village.MerchantRecipe;

/** A configured recipe that does not trigger vanilla's next-offer progression. */
public final class OwnedMerchantRecipe extends MerchantRecipe {

    public OwnedMerchantRecipe(ItemStack buy, ItemStack buySecondary, ItemStack sell) {
        super(buy, buySecondary, sell);
    }

    public OwnedMerchantRecipe(MerchantRecipe recipe) {
        super(recipe.writeToTags());
    }

    public OwnedMerchantRecipe(NBTTagCompound tags) {
        super(tags);
    }

    @Override
    public boolean hasSameIDsAs(MerchantRecipe other) {
        // EntityVillager uses this comparison against its final recipe to schedule
        // a vanilla unlock, reputation change, regeneration, and green particles.
        return false;
    }
}

