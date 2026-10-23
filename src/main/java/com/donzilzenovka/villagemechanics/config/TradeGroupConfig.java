package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class TradeGroupConfig {

    public String id;
    public IntRange offerCount = new IntRange(1);
    public List<WeightedTrade> pool = new ArrayList<WeightedTrade>();

    public TradeGroupConfig() {}

    public TradeGroupConfig(String id) {
        this.id = id;
    }
}
