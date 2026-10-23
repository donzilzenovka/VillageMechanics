package com.donzilzenovka.villagemechanics.network;

/** Thread-safe client state with no client-only class references. */
public final class TradeStateStore {

    private static volatile boolean[] finiteTrades = new boolean[0];
    private static volatile int[] remainingUses = new int[0];

    private TradeStateStore() {}

    public static synchronized void set(boolean[] finite, int[] remaining) {
        finiteTrades = finite == null ? new boolean[0] : finite.clone();
        remainingUses = remaining == null ? new int[0] : remaining.clone();
    }

    public static synchronized void update(int recipeIndex, int remaining) {
        if (recipeIndex < 0 || recipeIndex >= remainingUses.length) return;
        int[] updated = remainingUses.clone();
        updated[recipeIndex] = Math.max(0, remaining);
        remainingUses = updated;
    }

    public static int remainingFiniteUses(int recipeIndex) {
        boolean[] finite = finiteTrades;
        int[] remaining = remainingUses;
        if (recipeIndex < 0 || recipeIndex >= finite.length
            || !finite[recipeIndex]
            || recipeIndex >= remaining.length) {
            return -1;
        }
        return Math.max(0, remaining[recipeIndex]);
    }
}
