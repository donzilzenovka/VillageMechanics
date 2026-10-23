package com.donzilzenovka.villagemechanics.trade;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.IMerchant;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Replaces the packet-created client recipe list with the same exact matcher used by the server. */
@SideOnly(Side.CLIENT)
public final class ClientMerchantListHandler {

    private final Method renderToolTip = ReflectionHelper.findMethod(
        GuiScreen.class,
        null,
        new String[] { "renderToolTip", "func_146285_a" },
        ItemStack.class,
        Integer.TYPE,
        Integer.TYPE);
    private Method getNeiManager;
    private Method renderNeiToolTips;
    private boolean neiLookupAttempted;

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

    /** Repaints the hovered slot tooltip after GuiMerchant draws its late trade icons. */
    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMerchant)) return;
        if (redrawNeiTooltip((GuiContainer) event.gui, event.mouseX, event.mouseY)) return;

        Slot slot = ObfuscationReflectionHelper
            .getPrivateValue(GuiContainer.class, (GuiContainer) event.gui, "theSlot", "field_147006_u");
        Minecraft minecraft = Minecraft.getMinecraft();
        if (slot == null || !slot.getHasStack() || minecraft.thePlayer.inventory.getItemStack() != null) return;

        try {
            renderToolTip.invoke(event.gui, slot.getStack(), event.mouseX, event.mouseY);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not redraw the merchant inventory tooltip", exception);
        }
    }

    private boolean redrawNeiTooltip(GuiContainer gui, int mouseX, int mouseY) {
        if (!neiLookupAttempted) {
            neiLookupAttempted = true;
            try {
                Class<?> managerClass = Class.forName("codechicken.nei.guihook.GuiContainerManager");
                getNeiManager = managerClass.getMethod("getManager", GuiContainer.class);
                renderNeiToolTips = managerClass.getMethod("renderToolTips", Integer.TYPE, Integer.TYPE);
            } catch (Exception ignored) {
                getNeiManager = null;
                renderNeiToolTips = null;
            }
        }
        if (getNeiManager == null || renderNeiToolTips == null) return false;

        try {
            Object manager = getNeiManager.invoke(null, gui);
            if (manager == null) return false;
            renderNeiToolTips.invoke(manager, mouseX, mouseY);
            return true;
        } catch (Exception exception) {
            getNeiManager = null;
            renderNeiToolTips = null;
            return false;
        }
    }
}

