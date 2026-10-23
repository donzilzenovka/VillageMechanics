package com.donzilzenovka.villagemechanics.config;

public final class WeightedTrade {

    public String id;
    public int weight = 1;
    public ItemSpec buy;
    public ItemSpec buySecondary;
    public ItemSpec sell;
    public IntRange uses = new IntRange(3, 7);

    public WeightedTrade() {}

    public WeightedTrade(String id, int weight, ItemSpec buy, ItemSpec sell) {
        this.id = id;
        this.weight = weight;
        this.buy = buy;
        this.sell = sell;
    }
}
