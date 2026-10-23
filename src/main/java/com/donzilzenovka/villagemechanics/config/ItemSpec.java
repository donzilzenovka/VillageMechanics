package com.donzilzenovka.villagemechanics.config;

public final class ItemSpec {

    public String item;
    public int meta;
    public IntRange count = new IntRange(1);

    public ItemSpec() {}

    public ItemSpec(String item, int min, int max) {
        this.item = item;
        this.count = new IntRange(min, max);
    }

    public ItemSpec(String item, int count) {
        this(item, count, count);
    }
}
