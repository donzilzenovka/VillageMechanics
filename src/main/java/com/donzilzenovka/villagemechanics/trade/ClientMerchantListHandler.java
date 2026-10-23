package com.donzilzenovka.villagemechanics.trade;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.IMerchant;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.client.event.GuiScreenEvent;

import org.lwjgl.opengl.GL11;

import com.donzilzenovka.villagemechanics.network.TradeStateStore;

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
        GuiMerchant gui = (GuiMerchant) event.gui;
        drawFiniteTradeCounter(gui);
        if (redrawMerchantTooltip(gui, event.mouseX, event.mouseY)) return;
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

    private void drawFiniteTradeCounter(GuiMerchant gui) {
        int recipeIndex = ObfuscationReflectionHelper.getPrivateValue(GuiMerchant.class, gui, "field_147041_z");
        int remaining = TradeStateStore.remainingFiniteUses(recipeIndex);
        if (remaining < 0) return;

        Minecraft minecraft = Minecraft.getMinecraft();
        String label = String.valueOf(remaining);
        int guiLeft = (gui.width - 176) / 2;
        int guiTop = (gui.height - 166) / 2;
        int x = guiLeft + 139;
        int colour = remaining <= 0 ? 0xFF5555 : 0xFFFF55;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 300.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        minecraft.fontRenderer.drawStringWithShadow(label, x, guiTop + 28, colour);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }

    @SuppressWarnings("unchecked")
    private boolean redrawMerchantTooltip(GuiMerchant gui, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        MerchantRecipeList recipes = gui.func_147035_g()
            .getRecipes(minecraft.thePlayer);
        if (recipes == null || recipes.isEmpty()) return false;

        int recipeIndex = ObfuscationReflectionHelper.getPrivateValue(GuiMerchant.class, gui, "field_147041_z");
        if (recipeIndex < 0 || recipeIndex >= recipes.size()) return false;

        MerchantRecipe recipe = (MerchantRecipe) recipes.get(recipeIndex);
        int guiLeft = (gui.width - 176) / 2;
        int guiTop = (gui.height - 166) / 2;
        ItemStack hovered = null;
        if (inside(mouseX, mouseY, guiLeft + 36, guiTop + 24)) {
            hovered = recipe.getItemToBuy();
        } else if (recipe.getSecondItemToBuy() != null && inside(mouseX, mouseY, guiLeft + 62, guiTop + 24)) {
            hovered = recipe.getSecondItemToBuy();
        } else if (inside(mouseX, mouseY, guiLeft + 120, guiTop + 24)) {
            hovered = recipe.getItemToSell();
        }
        if (hovered == null) return false;

        try {
            renderToolTip.invoke(gui, hovered, mouseX, mouseY);
            return true;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not redraw the merchant trade tooltip", exception);
        }
    }

    private boolean inside(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 16;
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
