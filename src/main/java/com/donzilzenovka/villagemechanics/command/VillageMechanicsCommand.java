package com.donzilzenovka.villagemechanics.command;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.donzilzenovka.villagemechanics.config.ModConfiguration;
import com.donzilzenovka.villagemechanics.trade.TradeManager;

public final class VillageMechanicsCommand extends CommandBase {

    private static final double INSPECT_RANGE = 8.0D;

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
        EntityVillager target = targetedVillager(player);
        if (target == null) throw new CommandException("No villager under crosshair within 8 blocks.");

        int profession = target.getProfession();
        String created = tradeManager.discoverProfession(profession);
        if (created != null) {
            player.addChatMessage(new ChatComponentText("Created trade template " + created + "."));
        }

        IAttributeInstance attack = target.getEntityAttribute(SharedMonsterAttributes.attackDamage);
        player.addChatMessage(
            new ChatComponentText(
                "Villager professionId=" + profession
                    + " health="
                    + target.getHealth()
                    + "/"
                    + target.getMaxHealth()
                    + " attack="
                    + (attack == null ? "missing" : attack.getAttributeValue())
                    + " armour="
                    + target.getTotalArmorValue()));
        player.addChatMessage(new ChatComponentText("Equipment: " + equipment(target)));
    }

    @SuppressWarnings("unchecked")
    private EntityVillager targetedVillager(EntityPlayer player) {
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = start
            .addVector(look.xCoord * INSPECT_RANGE, look.yCoord * INSPECT_RANGE, look.zCoord * INSPECT_RANGE);

        double maximumDistance = INSPECT_RANGE;
        Vec3 blockStart = Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord);
        Vec3 blockEnd = Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord);
        MovingObjectPosition blockHit = player.worldObj.rayTraceBlocks(blockStart, blockEnd);
        if (blockHit != null) maximumDistance = start.distanceTo(blockHit.hitVec);

        AxisAlignedBB searchArea = player.boundingBox
            .addCoord(look.xCoord * INSPECT_RANGE, look.yCoord * INSPECT_RANGE, look.zCoord * INSPECT_RANGE)
            .expand(1.0D, 1.0D, 1.0D);
        List<Entity> entities = player.worldObj.getEntitiesWithinAABBExcludingEntity(player, searchArea);

        EntityVillager target = null;
        double targetDistance = maximumDistance;
        for (Entity entity : entities) {
            if (!(entity instanceof EntityVillager) || !entity.canBeCollidedWith()) continue;

            float border = entity.getCollisionBorderSize();
            AxisAlignedBB hitBox = entity.boundingBox.expand(border, border, border);
            MovingObjectPosition hit = hitBox.calculateIntercept(start, end);
            if (hitBox.isVecInside(start)) {
                if (targetDistance >= 0.0D) {
                    target = (EntityVillager) entity;
                    targetDistance = 0.0D;
                }
            } else if (hit != null) {
                double distance = start.distanceTo(hit.hitVec);
                if (distance <= targetDistance) {
                    target = (EntityVillager) entity;
                    targetDistance = distance;
                }
            }
        }
        return target;
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
