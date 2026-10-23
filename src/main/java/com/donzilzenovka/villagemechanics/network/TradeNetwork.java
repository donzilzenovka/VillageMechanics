package com.donzilzenovka.villagemechanics.network;

import net.minecraft.entity.player.EntityPlayerMP;

import com.donzilzenovka.villagemechanics.VillageMechanics;
import com.donzilzenovka.villagemechanics.trade.ExactMerchantRecipeList;
import com.donzilzenovka.villagemechanics.trade.OwnedMerchantRecipe;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class TradeNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE
        .newSimpleChannel(VillageMechanics.MOD_ID);

    private TradeNetwork() {}

    public static void initialize() {
        CHANNEL.registerMessage(TradeStateHandler.class, TradeStateMessage.class, 0, Side.CLIENT);
    }

    public static void sendFull(EntityPlayerMP player, ExactMerchantRecipeList recipes) {
        boolean[] finite = new boolean[recipes.size()];
        int[] remaining = new int[recipes.size()];
        for (int index = 0; index < recipes.size(); index++) {
            OwnedMerchantRecipe recipe = (OwnedMerchantRecipe) recipes.get(index);
            finite[index] = !recipe.shouldRestock();
            remaining[index] = recipe.remainingUses();
        }
        CHANNEL.sendTo(TradeStateMessage.full(finite, remaining), player);
    }

    public static void sendUpdate(EntityPlayerMP player, int recipeIndex, int remaining) {
        CHANNEL.sendTo(TradeStateMessage.update(recipeIndex, remaining), player);
    }

    public static void sendClear(EntityPlayerMP player) {
        CHANNEL.sendTo(TradeStateMessage.full(new boolean[0], new int[0]), player);
    }
}
