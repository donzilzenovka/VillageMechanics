package com.donzilzenovka.villagemechanics.config;

import java.util.ArrayList;
import java.util.List;

public final class ProfessionTradeConfig {

    public int schemaVersion = 2;
    public int profession;
    public List<WeightedTrade> pool = new ArrayList<WeightedTrade>();

    // Read compatibility for prototype schema 1 files. These are normalized into pool when loaded.
    public String mode;
    public Integer rolls;
    public List<WeightedTrade> trades;
}
