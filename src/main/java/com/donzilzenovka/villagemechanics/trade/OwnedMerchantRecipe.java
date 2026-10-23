package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.village.MerchantRecipe;

import com.donzilzenovka.villagemechanics.network.TradeNetwork;

/** A configured recipe that does not trigger vanilla's next-offer progression. */
public final class OwnedMerchantRecipe extends MerchantRecipe {

    private boolean restock = true;
    private int recipeIndex = -1;
    private EntityPlayerMP viewer;

    public OwnedMerchantRecipe(ItemStack buy, ItemStack buySecondary, ItemStack sell) {
        super(buy, buySecondary, sell);
    }

    public OwnedMerchantRecipe(MerchantRecipe recipe) {
        super(recipe.writeToTags());
    }

    public OwnedMerchantRecipe(NBTTagCompound tags) {
        super(tags);
    }

    public void configure(boolean shouldRestock, int index, EntityPlayerMP player) {
        restock = shouldRestock;
        recipeIndex = index;
        viewer = player;
    }

    public boolean shouldRestock() {
        return restock;
    }

    public int remainingUses() {
        NBTTagCompound tags = writeToTags();
        return Math.max(0, tags.getInteger("maxUses") - tags.getInteger("uses"));
    }

    @Override
    public void incrementToolUses() {
        super.incrementToolUses();
        if (!restock && viewer != null && recipeIndex >= 0) {
            TradeNetwork.sendUpdate(viewer, recipeIndex, remainingUses());
        }
    }

    @Override
    public boolean hasSameIDsAs(MerchantRecipe other) {
        // EntityVillager uses this comparison against its final recipe to schedule
        // a vanilla unlock, reputation change, regeneration, and green particles.
        return false;
    }
}
