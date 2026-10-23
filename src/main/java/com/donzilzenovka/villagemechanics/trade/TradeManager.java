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
import com.donzilzenovka.villagemechanics.config.WeightedTrade;

import cpw.mods.fml.common.registry.VillagerRegistry;

public final class TradeManager implements VillagerRegistry.IVillageTradeHandler {

    private final ModConfiguration configuration;
    private final Logger logger;
    private final Set<Integer> registeredProfessions = new HashSet<Integer>();
    private final Set<String> reportedInvalidItems = new HashSet<String>();
    private final Map<Integer, Map<String, WeightedTrade>> observedPools = new LinkedHashMap<Integer, Map<String, WeightedTrade>>();

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

    public synchronized void registerProfessions(Iterable<Integer> professions) {
        for (Integer profession : professions) {
            if (profession != null && profession.intValue() >= 0) registerProfession(profession.intValue());
        }
    }

    private void registerProfession(int profession) {
        if (registeredProfessions.add(Integer.valueOf(profession))) {
            VillagerRegistry.instance()
                .registerVillageTradeHandler(profession, this);
            logger.info("Registered trade-pool handler for profession {}", profession);
        }
    }

    public synchronized void reload() {
        configuration.load();
        register();
    }

    public synchronized String discoverProfession(int profession) {
        String created = configuration.discoverProfession(profession);
        registerProfession(profession);
        return created;
    }

    @SuppressWarnings("unchecked")
    public int[] recordObservedTrades(EntityVillager villager, EntityPlayer player) {
        MerchantRecipeList recipes = villager.getRecipes(player);
        List<WeightedTrade> observed = new ArrayList<WeightedTrade>();
        synchronized (this) {
            Map<String, WeightedTrade> pool = observedPools.get(Integer.valueOf(villager.getProfession()));
            if (pool != null) observed.addAll(pool.values());
        }
        if (recipes != null) {
            for (MerchantRecipe recipe : (List<MerchantRecipe>) recipes) {
                if (recipe == null) continue;
                observed.add(describe(recipe));
            }
        }
        return configuration.recordObservedTrades(villager.getProfession(), observed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void manipulateTradesForVillager(EntityVillager villager, MerchantRecipeList recipes, Random random) {
        observePool(villager.getProfession(), recipes);
        ProfessionTradeConfig config = configuration.getTradeConfig(villager.getProfession());
        if (config == null || config.pool == null || config.pool.isEmpty()) return;

        recipes.clear();

        List<WeightedTrade> candidates = new ArrayList<WeightedTrade>();
        for (WeightedTrade trade : config.pool) {
            if (trade != null && trade.weight > 0 && trade.buy != null && trade.sell != null) candidates.add(trade);
        }
        WeightedTrade selected = takeWeighted(candidates, random);
        MerchantRecipe recipe = selected == null ? null : createRecipe(selected, random);
        if (recipe != null) recipes.add(recipe);
    }

    @SuppressWarnings("unchecked")
    private synchronized void observePool(int profession, MerchantRecipeList recipes) {
        Map<String, WeightedTrade> pool = observedPools.get(Integer.valueOf(profession));
        if (pool == null) {
            pool = new LinkedHashMap<String, WeightedTrade>();
            observedPools.put(Integer.valueOf(profession), pool);
        }
        for (MerchantRecipe recipe : (List<MerchantRecipe>) recipes) {
            if (recipe == null) continue;
            WeightedTrade trade = describe(recipe);
            String key = tradeKey(trade);
            if (!pool.containsKey(key)) pool.put(key, trade);
        }
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
