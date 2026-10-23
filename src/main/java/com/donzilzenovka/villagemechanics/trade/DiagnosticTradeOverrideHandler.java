package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Diagnostic override used to establish which villager entities can be controlled at interaction time. */
public final class DiagnosticTradeOverrideHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.entityPlayer.worldObj.isRemote || !(event.target instanceof EntityVillager)) return;

        EntityVillager villager = (EntityVillager) event.target;
        MerchantRecipeList recipes = villager.getRecipes(event.entityPlayer);
        recipes.clear();
        recipes.add(new MerchantRecipe(new ItemStack(Blocks.dirt, 1), new ItemStack(Items.diamond, 1)));
    }
}
