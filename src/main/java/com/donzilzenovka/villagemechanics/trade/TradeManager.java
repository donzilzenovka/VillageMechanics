package com.donzilzenovka.villagemechanics.trade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

import org.apache.logging.log4j.Logger;

import com.donzilzenovka.villagemechanics.config.IntRange;
import com.donzilzenovka.villagemechanics.config.ItemSpec;
import com.donzilzenovka.villagemechanics.config.ModConfiguration;
import com.donzilzenovka.villagemechanics.config.ProfessionTradeConfig;
import com.donzilzenovka.villagemechanics.config.TradeGroupConfig;
import com.donzilzenovka.villagemechanics.config.WeightedTrade;

public final class TradeManager {

    private final ModConfiguration configuration;
    private final Logger logger;
    private final Set<String> reportedInvalidItems = new HashSet<String>();

    public TradeManager(ModConfiguration configuration, Logger logger) {
        this.configuration = configuration;
        this.logger = logger;
    }

    public synchronized void reload() {
        configuration.load();
    }

    public synchronized String discoverProfession(int profession) {
        return configuration.discoverProfession(profession);
    }

    @SuppressWarnings("unchecked")
    public void applyOwnedTrades(EntityVillager villager, EntityPlayer player) {
        int profession = villager.getProfession();
        if (profession == 5 || profession == 1210950779) return;

        ProfessionTradeConfig config = configuration.getTradeConfig(profession);
        if (config == null || config.groups == null || !hasTrades(config.groups)) return;

        int revision = poolRevision(config);
        long seed = villager.getUniqueID()
            .getMostSignificantBits()
            ^ villager.getUniqueID()
                .getLeastSignificantBits()
            ^ revision;
        Random random = new Random(seed);
        List<MerchantRecipe> expected = new ArrayList<MerchantRecipe>();
        for (TradeGroupConfig group : config.groups) {
            if (group == null || group.pool == null) continue;
            List<WeightedTrade> candidates = validCandidates(group.pool);
            IntRange offerCount = group.offerCount == null ? new IntRange(1) : group.offerCount;
            int requested = Math.max(0, offerCount.choose(random));
            int count = Math.min(requested, candidates.size());
            int added = 0;
            while (added < count && !candidates.isEmpty()) {
                WeightedTrade selected = takeWeighted(candidates, random);
                MerchantRecipe recipe = selected == null ? null : createRecipe(selected, random);
                if (recipe != null) {
                    expected.add(recipe);
                    added++;
                }
            }
        }

        MerchantRecipeList current = villager.getRecipes(player);
        Map<String, MerchantRecipe> preserved = new LinkedHashMap<String, MerchantRecipe>();
        for (MerchantRecipe recipe : (List<MerchantRecipe>) current) {
            if (recipe != null) preserved.put(recipeKey(recipe), recipe);
        }

        current.clear();
        for (MerchantRecipe recipe : expected) {
            MerchantRecipe existing = preserved.get(recipeKey(recipe));
            current.add(existing == null ? recipe : existing);
        }
        villager.getEntityData()
            .setInteger("VillageMechanicsTradeRevision", revision);
    }

    private boolean hasTrades(List<TradeGroupConfig> groups) {
        for (TradeGroupConfig group : groups) {
            if (group != null && group.pool != null && !group.pool.isEmpty()) return true;
        }
        return false;
    }

    private List<WeightedTrade> validCandidates(List<WeightedTrade> pool) {
        List<WeightedTrade> candidates = new ArrayList<WeightedTrade>();
        for (WeightedTrade trade : pool) {
            if (trade != null && trade.weight > 0 && trade.buy != null && trade.sell != null) candidates.add(trade);
        }
        return candidates;
    }

    private int poolRevision(ProfessionTradeConfig config) {
        int revision = 31 + config.profession;
        for (TradeGroupConfig group : config.groups) {
            if (group == null) continue;
            revision = 31 * revision + String.valueOf(group.id)
                .hashCode();
            revision = 31 * revision + rangeKey(group.offerCount).hashCode();
            if (group.pool == null) continue;
            for (WeightedTrade trade : group.pool) {
                if (trade != null) {
                    revision = 31 * revision + String.valueOf(trade.id)
                        .hashCode();
                    revision = 31 * revision + (31 * trade.weight + tradeKey(trade).hashCode());
                }
            }
        }
        return revision;
    }

    private String recipeKey(MerchantRecipe recipe) {
        NBTTagCompound tags = recipe.writeToTags();
        return itemStackKey(recipe.getItemToBuy()) + '|'
            + itemStackKey(recipe.getSecondItemToBuy())
            + '|'
            + itemStackKey(recipe.getItemToSell())
            + '|'
            + tags.getInteger("maxUses");
    }

    private String itemStackKey(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return "-";
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        String nbt = stack.hasTagCompound() ? stack.getTagCompound()
            .toString() : "null";
        return String.valueOf(name) + ':' + stack.getItemDamage() + ':' + stack.stackSize + ':' + nbt;
    }

    @SuppressWarnings("unchecked")
    public int[] recordObservedTrades(EntityVillager villager, EntityPlayer player) {
        MerchantRecipeList recipes = villager.getRecipes(player);
        List<WeightedTrade> observed = new ArrayList<WeightedTrade>();
        if (recipes != null) {
            for (MerchantRecipe recipe : (List<MerchantRecipe>) recipes) {
                if (recipe == null) continue;
                observed.add(describe(recipe));
            }
        }
        return configuration.recordObservedTrades(villager.getProfession(), observed);
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
        ItemStack stack = new ItemStack((Item) registered, count, spec.meta);
        if (spec.nbt != null && !spec.nbt.trim()
            .isEmpty()) {
            try {
                NBTBase parsed = JsonToNBT.func_150315_a(spec.nbt);
                if (!(parsed instanceof NBTTagCompound)) {
                    reportInvalidNbt(tradeId, "root is not a compound");
                    return null;
                }
                stack.setTagCompound((NBTTagCompound) parsed);
            } catch (Exception exception) {
                reportInvalidNbt(tradeId, exception.getMessage());
                return null;
            }
        }
        return stack;
    }

    private ItemSpec describe(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        Object name = Item.itemRegistry.getNameForObject(stack.getItem());
        if (name == null) return null;
        ItemSpec result = new ItemSpec(String.valueOf(name), stack.stackSize);
        result.meta = stack.getItemDamage();
        if (stack.hasTagCompound()) result.nbt = stack.getTagCompound()
            .toString();
        return result;
    }

    private WeightedTrade describe(MerchantRecipe recipe) {
        WeightedTrade trade = new WeightedTrade();
        trade.weight = 1;
        trade.buy = describe(recipe.getItemToBuy());
        trade.buySecondary = describe(recipe.getSecondItemToBuy());
        trade.sell = describe(recipe.getItemToSell());
        NBTTagCompound tags = recipe.writeToTags();
        trade.uses = new IntRange(Math.max(1, tags.getInteger("maxUses")));
        return trade;
    }

    private String tradeKey(WeightedTrade trade) {
        return stackKey(
            trade.buy) + '|' + stackKey(trade.buySecondary) + '|' + stackKey(trade.sell) + '|' + rangeKey(trade.uses);
    }

    private String stackKey(ItemSpec stack) {
        if (stack == null) return "-";
        return String.valueOf(
            stack.item) + ':' + stack.meta + ':' + rangeKey(stack.count) + ':' + String.valueOf(stack.nbt);
    }

    private String rangeKey(IntRange range) {
        return range == null ? "-" : range.min + ":" + range.max;
    }

    private synchronized void reportInvalid(String itemName, String tradeId) {
        String key = String.valueOf(itemName) + "@" + tradeId;
        if (reportedInvalidItems.add(key)) {
            logger.error("Skipping trade {} because item '{}' is not registered", tradeId, itemName);
        }
    }

    private synchronized void reportInvalidNbt(String tradeId, String reason) {
        String key = "nbt@" + tradeId;
        if (reportedInvalidItems.add(key))
            logger.error("Skipping trade {} because its item NBT is invalid: {}", tradeId, reason);
    }
}
