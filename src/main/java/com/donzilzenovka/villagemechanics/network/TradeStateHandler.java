package com.donzilzenovka.villagemechanics.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

public final class TradeStateHandler implements IMessageHandler<TradeStateMessage, IMessage> {

    @Override
    public IMessage onMessage(TradeStateMessage message, MessageContext context) {
        if (message.isFull()) {
            TradeStateStore.set(message.getFinite(), message.getRemaining());
        } else {
            TradeStateStore.update(message.getRecipeIndex(), message.getRemaining()[0]);
        }
        return null;
    }
}
