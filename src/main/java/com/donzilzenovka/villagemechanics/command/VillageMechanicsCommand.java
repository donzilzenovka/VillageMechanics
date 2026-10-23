package com.donzilzenovka.villagemechanics.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

import com.donzilzenovka.villagemechanics.config.ModConfiguration;
import com.donzilzenovka.villagemechanics.trade.TradeManager;

public final class VillageMechanicsCommand extends CommandBase {

    private final ModConfiguration configuration;
    private final TradeManager tradeManager;

    public VillageMechanicsCommand(ModConfiguration configuration, TradeManager tradeManager) {
        this.configuration = configuration;
        this.tradeManager = tradeManager;
    }

    @Override
    public String getCommandName() {
        return "villagemechanics";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/villagemechanics <reload|inspect>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        if (arguments.length != 1) throw new CommandException(getCommandUsage(sender));
        if ("reload".equalsIgnoreCase(arguments[0])) {
            tradeManager.reload();
            sender.addChatMessage(new ChatComponentText("Village Mechanics configuration reloaded."));
            return;
        }
        if ("inspect".equalsIgnoreCase(arguments[0]) && sender instanceof EntityPlayer) {
            inspect((EntityPlayer) sender);
            return;
        }
        throw new CommandException(getCommandUsage(sender));
    }

    @SuppressWarnings("unchecked")
    private void inspect(EntityPlayer player) throws CommandException {
        AxisAlignedBB area = player.boundingBox.expand(8.0D, 4.0D, 8.0D);
        List<EntityVillager> villagers = player.worldObj.getEntitiesWithinAABB(EntityVillager.class, area);
        EntityVillager nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (EntityVillager villager : villagers) {
            double distance = player.getDistanceSqToEntity(villager);
            if (distance < nearestDistance) {
                nearest = villager;
                nearestDistance = distance;
            }
        }
        if (nearest == null) throw new CommandException("No villager found within 8 blocks.");

        IAttributeInstance attack = nearest.getEntityAttribute(SharedMonsterAttributes.attackDamage);
        player.addChatMessage(
            new ChatComponentText(
                "Villager profession=" + nearest.getProfession()
                    + " health="
                    + nearest.getHealth()
                    + "/"
                    + nearest.getMaxHealth()
                    + " attack="
                    + (attack == null ? "missing" : attack.getAttributeValue())
                    + " armour="
                    + nearest.getTotalArmorValue()));
        player.addChatMessage(new ChatComponentText("Equipment: " + equipment(nearest)));
    }

    private String equipment(EntityVillager villager) {
        StringBuilder result = new StringBuilder();
        for (int slot = 4; slot >= 1; slot--) {
            if (result.length() > 0) result.append(", ");
            ItemStack stack = villager.getEquipmentInSlot(slot);
            Object name = stack == null ? null : Item.itemRegistry.getNameForObject(stack.getItem());
            result.append(slotName(slot))
                .append('=')
                .append(name == null ? "empty" : name);
        }
        return result.toString();
    }

    private String slotName(int slot) {
        switch (slot) {
            case 4:
                return "helmet";
            case 3:
                return "chest";
            case 2:
                return "legs";
            default:
                return "boots";
        }
    }
}
