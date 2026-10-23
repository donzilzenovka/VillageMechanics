package com.donzilzenovka.villagemechanics.config;

import java.util.Random;

public final class IntRange {

    public int min;
    public int max;

    public IntRange() {}

    public IntRange(int value) {
        this(value, value);
    }

    public IntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int choose(Random random) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + (high > low ? random.nextInt(high - low + 1) : 0);
    }
}
