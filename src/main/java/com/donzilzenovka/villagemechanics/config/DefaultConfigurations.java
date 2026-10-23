package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

import com.donzilzenovka.villagemechanics.config.CombatConfig.ArmourSet;
import com.donzilzenovka.villagemechanics.config.CombatConfig.ProfessionArmourConfig;

public final class DefaultConfigurations {

    private DefaultConfigurations() {}

    public static List<ProfessionTradeConfig> createTradeDefaults() {
        List<ProfessionTradeConfig> result = new ArrayList<ProfessionTradeConfig>();
        result.add(farmer());
        result.add(librarian());
        result.add(priest());
        result.add(blacksmith());
        result.add(butcher());
        return result;
    }

    public static String fileName(int profession) {
        switch (profession) {
            case 0:
                return "farmer.json";
            case 1:
                return "librarian.json";
            case 2:
                return "priest.json";
            case 3:
                return "blacksmith.json";
            case 4:
                return "butcher.json";
            default:
                return "profession_" + profession + ".json";
        }
    }

    public static CombatConfig createCombatDefault() {
        CombatConfig config = new CombatConfig();

        ProfessionArmourConfig blacksmith = new ProfessionArmourConfig();
        blacksmith.profession = 3;
        blacksmith.chance = 0.35D;
        blacksmith.sets.add(armour(10, "minecraft:iron_helmet", "minecraft:iron_chestplate", null, null));
        blacksmith.sets.add(
            armour(
                5,
                "minecraft:chainmail_helmet",
                "minecraft:chainmail_chestplate",
                "minecraft:chainmail_leggings",
                null));
        config.professionArmour.add(blacksmith);

        ProfessionArmourConfig butcher = new ProfessionArmourConfig();
        butcher.profession = 4;
        butcher.chance = 0.20D;
        butcher.sets.add(armour(10, null, "minecraft:leather_chestplate", "minecraft:leather_leggings", null));
        butcher.sets.add(armour(4, "minecraft:chainmail_helmet", "minecraft:chainmail_chestplate", null, null));
        config.professionArmour.add(butcher);

        return config;
    }

    private static ProfessionTradeConfig farmer() {
        ProfessionTradeConfig config = profession(0, 5);
        add(config, "wheat_for_emerald", 10, item("minecraft:wheat", 18, 22), item("minecraft:emerald", 1));
        add(config, "wool_for_emerald", 7, item("minecraft:wool", 16, 21), item("minecraft:emerald", 1));
        add(config, "raw_chicken_for_emerald", 6, item("minecraft:chicken", 14, 18), item("minecraft:emerald", 1));
        add(config, "emerald_for_bread", 8, item("minecraft:emerald", 1), item("minecraft:bread", 3, 4));
        add(config, "emerald_for_apples", 4, item("minecraft:emerald", 1), item("minecraft:apple", 4, 6));
        add(config, "emerald_for_melon", 4, item("minecraft:emerald", 1), item("minecraft:melon", 5, 8));
        return config;
    }

    private static ProfessionTradeConfig librarian() {
        ProfessionTradeConfig config = profession(1, 5);
        add(config, "paper_for_emerald", 10, item("minecraft:paper", 24, 36), item("minecraft:emerald", 1));
        add(config, "books_for_emerald", 7, item("minecraft:book", 8, 10), item("minecraft:emerald", 1));
        add(config, "emeralds_for_bookshelf", 6, item("minecraft:emerald", 3, 4), item("minecraft:bookshelf", 1));
        add(config, "emerald_for_glass", 5, item("minecraft:emerald", 1), item("minecraft:glass", 4, 5));
        add(config, "emeralds_for_compass", 2, item("minecraft:emerald", 10, 12), item("minecraft:compass", 1));
        return config;
    }

    private static ProfessionTradeConfig priest() {
        ProfessionTradeConfig config = profession(2, 5);
        add(
            config,
            "rotten_flesh_for_emerald",
            10,
            item("minecraft:rotten_flesh", 36, 40),
            item("minecraft:emerald", 1));
        add(config, "gold_for_emerald", 7, item("minecraft:gold_ingot", 8, 10), item("minecraft:emerald", 1));
        add(config, "emerald_for_redstone", 7, item("minecraft:emerald", 1), item("minecraft:redstone", 2, 4));
        add(config, "emerald_for_glowstone", 5, item("minecraft:emerald", 1), item("minecraft:glowstone_dust", 1, 3));
        add(config, "emeralds_for_ender_eye", 3, item("minecraft:emerald", 7, 11), item("minecraft:ender_eye", 1));
        add(
            config,
            "emeralds_for_experience_bottle",
            3,
            item("minecraft:emerald", 3, 5),
            item("minecraft:experience_bottle", 1));
        return config;
    }

    private static ProfessionTradeConfig blacksmith() {
        ProfessionTradeConfig config = profession(3, 6);
        add(config, "coal_for_emerald", 10, item("minecraft:coal", 16, 24), item("minecraft:emerald", 1));
        add(config, "iron_for_emerald", 9, item("minecraft:iron_ingot", 7, 9), item("minecraft:emerald", 1));
        add(config, "gold_for_emerald", 6, item("minecraft:gold_ingot", 8, 10), item("minecraft:emerald", 1));
        add(config, "diamond_for_emerald", 4, item("minecraft:diamond", 3, 4), item("minecraft:emerald", 1));
        add(config, "emeralds_for_iron_pickaxe", 7, item("minecraft:emerald", 7, 9), item("minecraft:iron_pickaxe", 1));
        add(
            config,
            "emeralds_for_iron_chestplate",
            5,
            item("minecraft:emerald", 10, 13),
            item("minecraft:iron_chestplate", 1));
        add(
            config,
            "emeralds_for_chain_boots",
            4,
            item("minecraft:emerald", 5, 7),
            item("minecraft:chainmail_boots", 1));
        return config;
    }

    private static ProfessionTradeConfig butcher() {
        ProfessionTradeConfig config = profession(4, 5);
        add(config, "raw_pork_for_emerald", 10, item("minecraft:porkchop", 14, 18), item("minecraft:emerald", 1));
        add(config, "raw_chicken_for_emerald", 8, item("minecraft:chicken", 14, 18), item("minecraft:emerald", 1));
        add(config, "coal_for_emerald", 6, item("minecraft:coal", 16, 24), item("minecraft:emerald", 1));
        add(
            config,
            "emerald_for_cooked_pork",
            7,
            item("minecraft:emerald", 1),
            item("minecraft:cooked_porkchop", 5, 7));
        add(
            config,
            "emerald_for_cooked_chicken",
            7,
            item("minecraft:emerald", 1),
            item("minecraft:cooked_chicken", 6, 8));
        add(
            config,
            "emeralds_for_leather_chestplate",
            4,
            item("minecraft:emerald", 6, 8),
            item("minecraft:leather_chestplate", 1));
        return config;
    }

    private static ProfessionTradeConfig profession(int profession, int rolls) {
        ProfessionTradeConfig config = new ProfessionTradeConfig();
        config.profession = profession;
        config.rolls = rolls;
        return config;
    }

    private static ItemSpec item(String name, int count) {
        return new ItemSpec(name, count);
    }

    private static ItemSpec item(String name, int min, int max) {
        return new ItemSpec(name, min, max);
    }

    private static void add(ProfessionTradeConfig config, String id, int weight, ItemSpec buy, ItemSpec sell) {
        config.trades.add(new WeightedTrade(id, weight, buy, sell));
    }

    private static ArmourSet armour(int weight, String helmet, String chestplate, String leggings, String boots) {
        ArmourSet set = new ArmourSet();
        set.weight = weight;
        set.helmet = helmet;
        set.chestplate = chestplate;
        set.leggings = leggings;
        set.boots = boots;
        return set;
    }
}
