package com.donzilzenovka.villagemechanics.trade;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Applies Village Mechanics-owned offers immediately before the normal merchant interface opens. */
public final class OwnedTradeInteractionHandler {

    private final TradeManager tradeManager;

    public OwnedTradeInteractionHandler(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityInteract(EntityInteractEvent event) {
        if (event.entityPlayer.worldObj.isRemote || !(event.target instanceof EntityVillager)) return;
        tradeManager.applyOwnedTrades((EntityVillager) event.target, event.entityPlayer);
    }
}
