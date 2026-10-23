package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class ProfessionTradeConfig {

    public int schemaVersion = 1;
    public int profession;
    public String mode = "replace";
    public int rolls = 5;
    public List<WeightedTrade> trades = new ArrayList<WeightedTrade>();
}
