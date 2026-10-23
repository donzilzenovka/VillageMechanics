package com.donzilzenovka.villagemechanics.combat;

import java.util.Random;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.apache.logging.log4j.Logger;

import com.donzilzenovka.villagemechanics.config.CombatConfig;
import com.donzilzenovka.villagemechanics.config.CombatConfig.ArmourSet;
import com.donzilzenovka.villagemechanics.config.CombatConfig.ProfessionArmourConfig;
import com.donzilzenovka.villagemechanics.config.ModConfiguration;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

public final class VillagerCombatHandler {

    private static final String COMBAT_VERSION_TAG = "VillageMechanicsCombatVersion";
    private static final int COMBAT_VERSION = 1;

    private final ModConfiguration configuration;
    private final Logger logger;
    private boolean dropChanceReflectionWarningReported;

    public VillagerCombatHandler(ModConfiguration configuration, Logger logger) {
        this.configuration = configuration;
        this.logger = logger;
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !(event.entity instanceof EntityVillager)) return;
        apply((EntityVillager) event.entity);
    }

    public void apply(EntityVillager villager) {
        CombatConfig config = configuration.getCombat();
        if (!config.enabled) return;

        boolean child = villager.isChild();
        if (!child || !config.adultsOnly) {
            applyAttribute(villager, SharedMonsterAttributes.maxHealth, config.maximumHealth);
            applyAttribute(villager, SharedMonsterAttributes.knockbackResistance, config.knockbackResistance);
            applyAttribute(villager, SharedMonsterAttributes.attackDamage, config.unarmedAttackDamage);
        }

        NBTTagCompound data = villager.getEntityData();
        if (data.getInteger(COMBAT_VERSION_TAG) >= COMBAT_VERSION) return;
        if (!child && config.naturalArmourEnabled) equipNaturalArmour(villager, config);
        data.setInteger(COMBAT_VERSION_TAG, COMBAT_VERSION);
    }

    private void applyAttribute(EntityVillager villager, net.minecraft.entity.ai.attributes.IAttribute attribute,
        double value) {
        IAttributeInstance instance = villager.getEntityAttribute(attribute);
        if (instance == null) {
            try {
                instance = villager.getAttributeMap()
                    .registerAttribute(attribute);
            } catch (IllegalArgumentException ignored) {
                instance = villager.getEntityAttribute(attribute);
            }
        }
        if (instance == null) {
            logger.error("Could not register villager attribute {}", attribute.getAttributeUnlocalizedName());
            return;
        }

        float oldMaximum = villager.getMaxHealth();
        boolean wasAtFullHealth = villager.getHealth() >= oldMaximum;
        instance.setBaseValue(value);
        if (attribute == SharedMonsterAttributes.maxHealth) {
            if (wasAtFullHealth) villager.setHealth(villager.getMaxHealth());
            else if (villager.getHealth() > villager.getMaxHealth()) villager.setHealth(villager.getMaxHealth());
        }
    }

    private void equipNaturalArmour(EntityVillager villager, CombatConfig config) {
        ProfessionArmourConfig professionConfig = findProfession(config, villager.getProfession());
        if (professionConfig == null || professionConfig.sets == null || professionConfig.sets.isEmpty()) return;

        Random random = villager.getRNG();
        if (random.nextDouble() >= clampChance(professionConfig.chance)) return;
        ArmourSet selected = chooseSet(professionConfig, random);
        if (selected == null) return;

        equip(villager, 4, selected.helmet, config.naturalArmourDropChance);
        equip(villager, 3, selected.chestplate, config.naturalArmourDropChance);
        equip(villager, 2, selected.leggings, config.naturalArmourDropChance);
        equip(villager, 1, selected.boots, config.naturalArmourDropChance);
    }

    private ProfessionArmourConfig findProfession(CombatConfig config, int profession) {
        if (config.professionArmour == null) return null;
        for (ProfessionArmourConfig candidate : config.professionArmour) {
            if (candidate != null && candidate.profession == profession) return candidate;
        }
        return null;
    }

    private ArmourSet chooseSet(ProfessionArmourConfig config, Random random) {
        int total = 0;
        for (ArmourSet set : config.sets) if (set != null) total += Math.max(0, set.weight);
        if (total <= 0) return null;
        int target = random.nextInt(total);
        for (ArmourSet set : config.sets) {
            if (set == null) continue;
            target -= Math.max(0, set.weight);
            if (target < 0) return set;
        }
        return null;
    }

    private void equip(EntityVillager villager, int slot, String itemName, float dropChance) {
        if (itemName == null || itemName.trim()
            .isEmpty() || villager.getEquipmentInSlot(slot) != null) return;
        Object registered = Item.itemRegistry.getObject(itemName);
        if (!(registered instanceof ItemArmor)) {
            logger.error("Natural armour item '{}' is missing or is not armour", itemName);
            return;
        }
        villager.setCurrentItemOrArmor(slot, new ItemStack((Item) registered));
        setDropChance(villager, slot, dropChance);
    }

    private void setDropChance(EntityVillager villager, int slot, float value) {
        try {
            float[] chances = ReflectionHelper
                .getPrivateValue(EntityLiving.class, villager, "equipmentDropChances", "field_82174_bp");
            if (chances != null && slot >= 0 && slot < chances.length) chances[slot] = Math.max(0.0F, value);
        } catch (RuntimeException exception) {
            if (!dropChanceReflectionWarningReported) {
                dropChanceReflectionWarningReported = true;
                logger.warn("Could not set natural armour drop chance; using the entity default", exception);
            }
        }
    }

    private double clampChance(double chance) {
        return Math.max(0.0D, Math.min(1.0D, chance));
    }
}
