package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class CombatConfig {

    public int schemaVersion = 1;
    public boolean enabled = true;
    public boolean adultsOnly = true;
    public double maximumHealth = 30.0D;
    public double unarmedAttackDamage = 5.0D;
    public double knockbackResistance = 0.15D;
    public boolean naturalArmourEnabled = true;
    public float naturalArmourDropChance = 0.0F;
    public List<ProfessionArmourConfig> professionArmour = new ArrayList<ProfessionArmourConfig>();

    public static final class ProfessionArmourConfig {

        public int profession;
        public double chance;
        public List<ArmourSet> sets = new ArrayList<ArmourSet>();
    }

    public static final class ArmourSet {

        public int weight = 1;
        public String helmet;
        public String chestplate;
        public String leggings;
        public String boots;
    }
}
