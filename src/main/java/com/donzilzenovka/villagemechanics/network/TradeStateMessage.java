package com.donzilzenovka.villagemechanics.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class TradeStateMessage implements IMessage {

    private boolean full;
    private int recipeIndex;
    private boolean[] finite;
    private int[] remaining;

    public TradeStateMessage() {}

    public static TradeStateMessage full(boolean[] finite, int[] remaining) {
        TradeStateMessage message = new TradeStateMessage();
        message.full = true;
        message.finite = finite;
        message.remaining = remaining;
        return message;
    }

    public static TradeStateMessage update(int recipeIndex, int remaining) {
        TradeStateMessage message = new TradeStateMessage();
        message.recipeIndex = recipeIndex;
        message.remaining = new int[] { remaining };
        return message;
    }

    public boolean isFull() {
        return full;
    }

    public int getRecipeIndex() {
        return recipeIndex;
    }

    public boolean[] getFinite() {
        return finite;
    }

    public int[] getRemaining() {
        return remaining;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        full = buffer.readBoolean();
        if (full) {
            int size = buffer.readUnsignedByte();
            finite = new boolean[size];
            remaining = new int[size];
            for (int index = 0; index < size; index++) {
                finite[index] = buffer.readBoolean();
                remaining[index] = buffer.readInt();
            }
        } else {
            recipeIndex = buffer.readUnsignedByte();
            remaining = new int[] { buffer.readInt() };
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(full);
        if (full) {
            buffer.writeByte(finite.length);
            for (int index = 0; index < finite.length; index++) {
                buffer.writeBoolean(finite[index]);
                buffer.writeInt(remaining[index]);
            }
        } else {
            buffer.writeByte(recipeIndex);
            buffer.writeInt(remaining[0]);
        }
    }
}
