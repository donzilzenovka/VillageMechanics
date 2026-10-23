package com.donzilzenovka.villagemechanics.config;

public final class DefaultConfigurations {

    private DefaultConfigurations() {}

    public static String fileName(int profession) {
        return "profession_" + profession + ".json";
    }

    public static CombatConfig createCombatDefault() {
        return new CombatConfig();
    }

    public static ProfessionTradeConfig createDiscoveredProfessionDefault(int profession) {
        ProfessionTradeConfig config = new ProfessionTradeConfig();
        config.profession = profession;
        config.mode = "append";
        config.rolls = 0;
        return config;
    }
}
