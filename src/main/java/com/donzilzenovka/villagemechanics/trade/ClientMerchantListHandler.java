package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.entity.IMerchant;
import net.minecraft.village.MerchantRecipeList;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Replaces the packet-created client recipe list with the same exact matcher used by the server. */
@SideOnly(Side.CLIENT)
public final class ClientMerchantListHandler {

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        if (!(minecraft.currentScreen instanceof GuiMerchant)) return;

        IMerchant merchant = ((GuiMerchant) minecraft.currentScreen).func_147035_g();
        MerchantRecipeList recipes = merchant.getRecipes(minecraft.thePlayer);
        if (recipes == null || recipes instanceof ExactMerchantRecipeList) return;

        ExactMerchantRecipeList exact = new ExactMerchantRecipeList();
        exact.addAll(recipes);
        merchant.setRecipes(exact);
    }
}
