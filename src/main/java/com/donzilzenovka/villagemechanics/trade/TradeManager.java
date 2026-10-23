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
import net.minecraft.entity.player.EntityPlayerMP;
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
import com.donzilzenovka.villagemechanics.network.TradeNetwork;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

public final class TradeManager {

    private static final String ORIGINAL_TRADES_TAG = "VillageMechanicsOriginalTrades";
    private static final String TRADE_REVISION_TAG = "VillageMechanicsTradeRevision";
    private static final String LAST_RESTOCK_DAY_TAG = "VillageMechanicsLastRestockWorldDay";
    private static final String LEGACY_RESTOCK_DAY_TAG = "VillageMechanicsLastRestockDay";

    private final ModConfiguration configuration;
    private final Logger logger;
    private final Set<String> reportedInvalidItems = new HashSet<String>();

    public TradeManager(ModConfiguration configuration, Logger logger) {
        this.configuration = configuration;
        this.logger = logger;
    }

    public synchronized List<String> reloadAndValidate() {
        configuration.load();
        return validateConfiguration();
    }

    private List<String> validateConfiguration() {
        List<String> diagnostics = configuration.getDiagnostics();
        for (Map.Entry<Integer, ProfessionTradeConfig> entry : configuration.getTrades()
            .entrySet()) {
            ProfessionTradeConfig config = entry.getValue();
            String source = configuration.getTradeSource(
                entry.getKey()
                    .intValue());
            if (config.schemaVersion < 1 || config.schemaVersion > 4) {
                diagnostics.add(source + ": unsupported schemaVersion " + config.schemaVersion);
            }
            if (config.groups == null) continue;
            for (TradeGroupConfig group : config.groups) {
                if (group == null) continue;
                validateRange(diagnostics, source, "group '" + group.id + "' offerCount", group.offerCount, true);
                if (group.pool == null) continue;
                for (WeightedTrade trade : group.pool) validateTrade(diagnostics, source, trade);
            }
        }
        return diagnostics;
    }

    private void validateTrade(List<String> diagnostics, String source, WeightedTrade trade) {
        if (trade == null) return;
        String label = "trade '" + trade.id + "'";
        if (trade.weight <= 0) diagnostics.add(source + ": " + label + " weight must be greater than zero");
        validateItem(diagnostics, source, label + " buy", trade.buy);
        validateItem(diagnostics, source, label + " buySecondary", trade.buySecondary);
        validateItem(diagnostics, source, label + " sell", trade.sell);
        validateRange(diagnostics, source, label + " uses", trade.uses, false);
    }

    private void validateItem(List<String> diagnostics, String source, String label, ItemSpec spec) {
        if (spec == null) {
            if (!label.endsWith("buySecondary")) diagnostics.add(source + ": " + label + " is missing");
            return;
        }
        if (spec.item == null || spec.item.trim()
            .isEmpty() || !(Item.itemRegistry.getObject(spec.item) instanceof Item)) {
            diagnostics.add(source + ": " + label + " item '" + spec.item + "' is not registered");
        }
        validateRange(diagnostics, source, label + " count", spec.count, false);
        if (spec.nbt != null && !spec.nbt.trim()
            .isEmpty()) {
            try {
                if (!(JsonToNBT.func_150315_a(spec.nbt) instanceof NBTTagCompound)) {
                    diagnostics.add(source + ": " + label + " NBT root must be a compound");
                }
            } catch (Exception exception) {
                diagnostics.add(source + ": " + label + " has invalid NBT: " + exception.getMessage());
            }
        }
    }

    private void validateRange(List<String> diagnostics, String source, String label, IntRange range,
        boolean allowZero) {
        if (range == null) {
            diagnostics.add(source + ": " + label + " is missing");
            return;
        }
        int minimum = allowZero ? 0 : 1;
        if (range.min < minimum || range.max < minimum) {
            diagnostics.add(source + ": " + label + " values must be at least " + minimum);
        }
        if (range.min > range.max) diagnostics.add(source + ": " + label + " min exceeds max");
    }

    public synchronized String discoverProfession(int profession) {
        return configuration.discoverProfession(profession);
    }

    @SuppressWarnings("unchecked")
    public void applyOwnedTrades(EntityVillager villager, EntityPlayer player) {
        int profession = villager.getProfession();
        ProfessionTradeConfig config = configuration.getTradeConfig(profession);
        if (config == null || config.groups == null || !hasTrades(config.groups)) {
            restoreOriginalTrades(villager, player);
            if (player instanceof EntityPlayerMP) TradeNetwork.sendClear((EntityPlayerMP) player);
            return;
        }

        MerchantRecipeList current = villager.getRecipes(player);
        rememberOriginalTrades(villager, current);
        NBTTagCompound entityData = villager.getEntityData();
        // Use the visible Minecraft clock. Commands such as /time add and
        // time-scaling mods advance worldTime, but do not necessarily advance
        // the world's separate total tick counter at the same rate.
        long currentDay = villager.worldObj.getWorldTime() / 24000L;
        boolean restock = entityData.hasKey(LEGACY_RESTOCK_DAY_TAG, 99)
            || entityData.hasKey(LAST_RESTOCK_DAY_TAG, 99) && currentDay > entityData.getLong(LAST_RESTOCK_DAY_TAG);

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

        Map<String, MerchantRecipe> preserved = new LinkedHashMap<String, MerchantRecipe>();
        for (MerchantRecipe recipe : (List<MerchantRecipe>) current) {
            if (recipe != null) preserved.put(recipeKey(recipe), recipe);
        }

        ExactMerchantRecipeList controlled = new ExactMerchantRecipeList();
        for (MerchantRecipe recipe : expected) {
            MerchantRecipe existing = preserved.get(recipeKey(recipe));
            boolean shouldRestock = ((OwnedMerchantRecipe) recipe).shouldRestock();
            controlled
                .add(existing == null ? recipe : copyOwnedRecipe(existing, restock && shouldRestock, shouldRestock));
        }
        EntityPlayerMP viewer = player instanceof EntityPlayerMP ? (EntityPlayerMP) player : null;
        for (int index = 0; index < controlled.size(); index++) {
            ((OwnedMerchantRecipe) controlled.get(index))
                .configure(((OwnedMerchantRecipe) controlled.get(index)).shouldRestock(), index, viewer);
        }
        ObfuscationReflectionHelper
            .setPrivateValue(EntityVillager.class, villager, controlled, "buyingList", "field_70963_i");
        entityData.setInteger(TRADE_REVISION_TAG, revision);
        entityData.setLong(LAST_RESTOCK_DAY_TAG, currentDay);
        entityData.removeTag(LEGACY_RESTOCK_DAY_TAG);
        if (viewer != null) TradeNetwork.sendFull(viewer, controlled);
    }

    private MerchantRecipe copyOwnedRecipe(MerchantRecipe recipe, boolean resetUses, boolean shouldRestock) {
        NBTTagCompound tags = recipe.writeToTags();
        if (resetUses) tags.setInteger("uses", 0);
        OwnedMerchantRecipe copy = new OwnedMerchantRecipe(tags);
        copy.configure(shouldRestock, -1, null);
        return copy;
    }

    private void rememberOriginalTrades(EntityVillager villager, MerchantRecipeList current) {
        NBTTagCompound entityData = villager.getEntityData();
        if (entityData.hasKey(ORIGINAL_TRADES_TAG, 10) || entityData.hasKey(TRADE_REVISION_TAG, 99)) return;
        entityData.setTag(ORIGINAL_TRADES_TAG, current.getRecipiesAsTags());
    }

    private void restoreOriginalTrades(EntityVillager villager, EntityPlayer player) {
        NBTTagCompound entityData = villager.getEntityData();
        if (!entityData.hasKey(TRADE_REVISION_TAG, 99)) return;

        MerchantRecipeList restored = entityData.hasKey(ORIGINAL_TRADES_TAG, 10)
            ? new MerchantRecipeList(entityData.getCompoundTag(ORIGINAL_TRADES_TAG))
            : null;
        ObfuscationReflectionHelper
            .setPrivateValue(EntityVillager.class, villager, restored, "buyingList", "field_70963_i");
        entityData.removeTag(ORIGINAL_TRADES_TAG);
        entityData.removeTag(TRADE_REVISION_TAG);
        entityData.removeTag(LAST_RESTOCK_DAY_TAG);
        entityData.removeTag(LEGACY_RESTOCK_DAY_TAG);

        // Villagers controlled by builds predating the snapshot regenerate their
        // original Forge/vanilla pool once, because no saved list exists for them.
        if (restored == null) villager.getRecipes(player);
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
                    revision = 31 * revision + (trade.restock ? 1 : 0);
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

        OwnedMerchantRecipe recipe = new OwnedMerchantRecipe(buy, buySecondary, sell);
        recipe.configure(definition.restock, -1, null);
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
