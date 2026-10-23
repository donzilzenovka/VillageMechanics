package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class ProfessionTradeConfig {

    public int schemaVersion = 2;
    public int profession;
    public String mode = "replace";
    public int rolls = 5;
    public List<TradeRemoval> remove = new ArrayList<TradeRemoval>();
    public List<WeightedTrade> trades = new ArrayList<WeightedTrade>();
}
