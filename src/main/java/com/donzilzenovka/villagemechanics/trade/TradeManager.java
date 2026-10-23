package com.donzilzenovka.villagemechanics.trade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import org.apache.logging.log4j.Logger;

import com.donzilzenovka.villagemechanics.config.IntRange;
import com.donzilzenovka.villagemechanics.config.ItemMatch;
import com.donzilzenovka.villagemechanics.config.ItemSpec;
import com.donzilzenovka.villagemechanics.config.ModConfiguration;
import com.donzilzenovka.villagemechanics.config.ProfessionTradeConfig;
import com.donzilzenovka.villagemechanics.config.TradeRemoval;
import com.donzilzenovka.villagemechanics.config.WeightedTrade;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.VillagerRegistry;

public final class TradeManager implements VillagerRegistry.IVillageTradeHandler {

    private final ModConfiguration configuration;
    private final Logger logger;
    private final Set<Integer> registeredProfessions = new HashSet<Integer>();
    private final Set<String> reportedInvalidItems = new HashSet<String>();

    public TradeManager(ModConfiguration configuration, Logger logger) {
        this.configuration = configuration;
        this.logger = logger;
    }

    public synchronized void register() {
        for (Integer profession : configuration.getTrades()
            .keySet()) {
            if (registeredProfessions.add(profession)) {
                VillagerRegistry.instance()
                    .registerVillageTradeHandler(profession.intValue(), this);
                logger.info("Registered weighted trade handler for profession {}", profession);
            }
        }
    }

    public synchronized void reload() {
        configuration.load();
        register();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.world.isRemote || !(event.entity instanceof EntityVillager)) return;
        int profession = ((EntityVillager) event.entity).getProfession();
        if (configuration.getTradeConfig(profession) != null) return;
        configuration.discoverProfession(profession);
        register();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void manipulateTradesForVillager(EntityVillager villager, MerchantRecipeList recipes, Random random) {
        ProfessionTradeConfig config = configuration.getTradeConfig(villager.getProfession());
        if (config == null) return;
        if ("replace".equalsIgnoreCase(config.mode)) {
            recipes.clear();
        } else {
            removeMatchingRecipes(config.remove, recipes);
        }

        List<WeightedTrade> candidates = new ArrayList<WeightedTrade>();
        for (WeightedTrade trade : config.trades) {
            if (trade != null && trade.weight > 0 && trade.buy != null && trade.sell != null) candidates.add(trade);
        }

        int rolls = Math.min(config.rolls, candidates.size());
        for (int roll = 0; roll < rolls && !candidates.isEmpty(); roll++) {
            WeightedTrade selected = takeWeighted(candidates, random);
            if (selected == null) break;
            MerchantRecipe recipe = createRecipe(selected, random);
            if (recipe != null) recipes.add(recipe);
        }
    }

    @SuppressWarnings("unchecked")
    private void removeMatchingRecipes(List<TradeRemoval> removals, MerchantRecipeList recipes) {
        if (removals == null || removals.isEmpty()) return;
        Iterator<MerchantRecipe> iterator = recipes.iterator();
        while (iterator.hasNext()) {
            MerchantRecipe recipe = iterator.next();
            if (matchesAny(removals, recipe)) iterator.remove();
        }
    }

    private boolean matchesAny(List<TradeRemoval> removals, MerchantRecipe recipe) {
        for (TradeRemoval removal : removals) {
            if (removal != null && matches(removal.buy, recipe.getItemToBuy())
                && matches(removal.buySecondary, recipe.getSecondItemToBuy())
                && matches(removal.sell, recipe.getItemToSell())) return true;
        }
        return false;
    }

    private boolean matches(ItemMatch expected, ItemStack actual) {
        if (expected == null) return true;
        if (actual == null) return false;
        if (expected.item != null && !expected.item.trim()
            .isEmpty()) {
            Object name = Item.itemRegistry.getNameForObject(actual.getItem());
            if (!expected.item.equals(String.valueOf(name))) return false;
        }
        if (expected.meta != null && expected.meta.intValue() != actual.getItemDamage()) return false;
        if (expected.count != null) {
            int minimum = Math.min(expected.count.min, expected.count.max);
            int maximum = Math.max(expected.count.min, expected.count.max);
            if (actual.stackSize < minimum || actual.stackSize > maximum) return false;
        }
        return true;
    }

    private WeightedTrade takeWeighted(List<WeightedTrade> candidates, Random random) {
        long total = 0L;
        for (WeightedTrade candidate : candidates) total += Math.max(0, candidate.weight);
        if (total <= 0L) return null;

        long target = nextLong(random, total);
        long cursor = 0L;
        for (int index = 0; index < candidates.size(); index++) {
            WeightedTrade candidate = candidates.get(index);
            cursor += candidate.weight;
            if (target < cursor) {
                candidates.remove(index);
                return candidate;
            }
        }
        return candidates.remove(candidates.size() - 1);
    }

    private long nextLong(Random random, long bound) {
        if (bound <= Integer.MAX_VALUE) return random.nextInt((int) bound);
        long bits;
        long value;
        do {
            bits = random.nextLong() & Long.MAX_VALUE;
            value = bits % bound;
        } while (bits - value + (bound - 1L) < 0L);
        return value;
    }

    private MerchantRecipe createRecipe(WeightedTrade definition, Random random) {
        ItemStack buy = createStack(definition.buy, random, definition.id);
        ItemStack sell = createStack(definition.sell, random, definition.id);
        ItemStack buySecondary = createStack(definition.buySecondary, random, definition.id);
        if (buy == null || sell == null) return null;

        MerchantRecipe recipe = new MerchantRecipe(buy, buySecondary, sell);
        IntRange uses = definition.uses == null ? new IntRange(3, 7) : definition.uses;
        int requestedUses = Math.max(1, uses.choose(random));
        recipe.func_82783_a(requestedUses - 7);
        return recipe;
    }

    private ItemStack createStack(ItemSpec spec, Random random, String tradeId) {
        if (spec == null) return null;
        if (spec.item == null || spec.item.trim()
            .isEmpty()) {
            reportInvalid(spec.item, tradeId);
            return null;
        }
        Object registered = Item.itemRegistry.getObject(spec.item);
        if (!(registered instanceof Item)) {
            reportInvalid(spec.item, tradeId);
            return null;
        }
        int requested = spec.count == null ? 1 : spec.count.choose(random);
        int count = Math.max(1, Math.min(requested, ((Item) registered).getItemStackLimit()));
        return new ItemStack((Item) registered, count, spec.meta);
    }

    private synchronized void reportInvalid(String itemName, String tradeId) {
        String key = String.valueOf(itemName) + "@" + tradeId;
        if (reportedInvalidItems.add(key)) {
            logger.error("Skipping trade {} because item '{}' is not registered", tradeId, itemName);
        }
    }
}
