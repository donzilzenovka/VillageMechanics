package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class ProfessionTradeConfig {

    public int schemaVersion = 4;
    public int profession;
    public List<TradeGroupConfig> groups = new ArrayList<TradeGroupConfig>();

    // Read compatibility for prototype schema 1-3 files. These are normalized into one default group.
    public IntRange offerCount;
    public List<WeightedTrade> pool;
    public String mode;
    public Integer rolls;
    public List<WeightedTrade> trades;
}
