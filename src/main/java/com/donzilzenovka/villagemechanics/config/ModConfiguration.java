package com.donzilzenovka.villagemechanics.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ModConfiguration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private final File root;
    private final File tradesDirectory;
    private final File tradePoolsDirectory;
    private final File combatFile;
    private final Logger logger;
    private volatile CombatConfig combat = new CombatConfig();
    private volatile Map<Integer, ProfessionTradeConfig> trades = Collections.emptyMap();
    private volatile Map<Integer, String> tradeSources = Collections.emptyMap();
    private volatile List<String> diagnostics = Collections.emptyList();
    private List<String> loadingDiagnostics = new ArrayList<String>();
    private Map<Integer, String> loadingTradeSources = new HashMap<Integer, String>();

    public ModConfiguration(File root, Logger logger) {
        this.root = root;
        this.tradesDirectory = new File(root, "trades");
        this.tradePoolsDirectory = new File(root, "trade_pools");
        this.combatFile = new File(root, "combat.json");
        this.logger = logger;
    }

    public synchronized void load() {
        loadingDiagnostics = new ArrayList<String>();
        loadingTradeSources = new HashMap<Integer, String>();
        ensureDirectories();
        writeIfMissing(combatFile, DefaultConfigurations.createCombatDefault());

        combat = read(combatFile, CombatConfig.class, DefaultConfigurations.createCombatDefault());
        trades = Collections.unmodifiableMap(readTradeFiles());
        tradeSources = Collections.unmodifiableMap(new HashMap<Integer, String>(loadingTradeSources));
        diagnostics = Collections.unmodifiableList(new ArrayList<String>(loadingDiagnostics));
        logger.info("Loaded {} profession trade configurations", trades.size());
    }

    public synchronized String discoverProfession(int profession) {
        if (profession < 0 || trades.containsKey(Integer.valueOf(profession))) return null;

        ensureDirectories();
        ProfessionTradeConfig config = DefaultConfigurations.createDiscoveredProfessionDefault(profession);
        String name = DefaultConfigurations.fileName(profession);
        File active = new File(tradesDirectory, name);
        boolean created = !active.exists();
        writeIfMissing(active, config);
        load();

        if (created && trades.containsKey(Integer.valueOf(profession))) {
            logger.info("Created empty authoritative pool {} for inspected profession {}", name, profession);
            return name;
        }
        return null;
    }

    public synchronized int[] recordObservedTrades(int profession, List<WeightedTrade> observed) {
        ensureDirectories();
        String name = DefaultConfigurations.fileName(profession);
        File reference = new File(tradePoolsDirectory, name);
        ProfessionTradeConfig catalogue = read(reference, ProfessionTradeConfig.class, null);
        if (catalogue == null) catalogue = DefaultConfigurations.createDiscoveredProfessionDefault(profession);
        normalize(catalogue, reference);

        Map<String, WeightedTrade> unique = new LinkedHashMap<String, WeightedTrade>();
        for (TradeGroupConfig group : catalogue.groups) {
            if (group == null || group.pool == null) continue;
            for (WeightedTrade trade : group.pool) {
                if (trade != null && trade.buy != null && trade.sell != null) unique.put(tradeKey(trade), trade);
            }
        }

        int before = unique.size();
        for (WeightedTrade trade : observed) {
            if (trade != null && trade.buy != null && trade.sell != null) {
                String key = tradeKey(trade);
                if (!unique.containsKey(key)) unique.put(key, trade);
            }
        }

        catalogue.profession = profession;
        catalogue.schemaVersion = 4;
        catalogue.offerCount = null;
        catalogue.pool = null;
        catalogue.mode = null;
        catalogue.rolls = null;
        catalogue.trades = null;
        TradeGroupConfig observedGroup = new TradeGroupConfig("observed");
        observedGroup.pool = new ArrayList<WeightedTrade>(unique.values());
        observedGroup.offerCount = new IntRange(observedGroup.pool.size());
        catalogue.groups = new ArrayList<TradeGroupConfig>();
        catalogue.groups.add(observedGroup);
        for (int index = 0; index < observedGroup.pool.size(); index++) {
            observedGroup.pool.get(index).id = "observed_" + (index + 1);
        }
        writeReplacing(reference, catalogue);
        return new int[] { observed.size(), unique.size() - before, unique.size() };
    }

    public CombatConfig getCombat() {
        return combat;
    }

    public Map<Integer, ProfessionTradeConfig> getTrades() {
        return trades;
    }

    public ProfessionTradeConfig getTradeConfig(int profession) {
        return trades.get(Integer.valueOf(profession));
    }

    public String getTradeSource(int profession) {
        String source = tradeSources.get(Integer.valueOf(profession));
        return source == null ? "profession " + profession : source;
    }

    public List<String> getDiagnostics() {
        return new ArrayList<String>(diagnostics);
    }

    private void ensureDirectories() {
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Could not create " + root);
        }
        if (!tradesDirectory.isDirectory() && !tradesDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create " + tradesDirectory);
        }
        if (!tradePoolsDirectory.isDirectory() && !tradePoolsDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create " + tradePoolsDirectory);
        }
    }

    private Map<Integer, ProfessionTradeConfig> readTradeFiles() {
        Map<Integer, ProfessionTradeConfig> result = new HashMap<Integer, ProfessionTradeConfig>();
        File[] files = tradesDirectory.listFiles();
        if (files == null) return result;

        Arrays.sort(files, new Comparator<File>() {

            @Override
            public int compare(File left, File right) {
                return left.getName()
                    .compareToIgnoreCase(right.getName());
            }
        });

        for (File file : files) {
            if (!file.isFile() || !file.getName()
                .toLowerCase()
                .endsWith(".json")) continue;
            ProfessionTradeConfig config = read(file, ProfessionTradeConfig.class, null);
            if (config != null) normalize(config, file);
            if (config == null || !validate(config, file)) continue;
            ProfessionTradeConfig replaced = result.put(Integer.valueOf(config.profession), config);
            loadingTradeSources.put(Integer.valueOf(config.profession), file.getName());
            if (replaced != null) {
                logger.warn("{} overrides another file for profession {}", file.getName(), config.profession);
            }
        }
        return result;
    }

    private boolean validate(ProfessionTradeConfig config, File file) {
        Set<String> groupIds = new HashSet<String>();
        Set<String> tradeIds = new HashSet<String>();
        for (TradeGroupConfig group : config.groups) {
            if (group == null || group.id == null
                || group.id.trim()
                    .isEmpty()) {
                logger.error("Ignoring {}: every trade group requires a non-empty id", file.getName());
                addDiagnostic(file, "every trade group requires a non-empty id");
                return false;
            }
            if (!groupIds.add(group.id)) {
                logger.error("Ignoring {}: duplicate trade group id '{}'", file.getName(), group.id);
                addDiagnostic(file, "duplicate trade group id '" + group.id + "'");
                return false;
            }
            if (group.pool == null) group.pool = new ArrayList<WeightedTrade>();
            if (group.offerCount == null) group.offerCount = new IntRange(1);
            for (WeightedTrade trade : group.pool) {
                if (trade == null || trade.id == null
                    || trade.id.trim()
                        .isEmpty()) {
                    logger.error("Ignoring {}: every trade requires a non-empty id", file.getName());
                    addDiagnostic(file, "every trade requires a non-empty id");
                    return false;
                }
                if (!tradeIds.add(trade.id)) {
                    logger.error("Ignoring {}: duplicate trade id '{}'", file.getName(), trade.id);
                    addDiagnostic(file, "duplicate trade id '" + trade.id + "'");
                    return false;
                }
            }
        }
        return true;
    }

    private void normalize(ProfessionTradeConfig config, File file) {
        if (config.groups == null) config.groups = new ArrayList<TradeGroupConfig>();
        if ((config.pool == null || config.pool.isEmpty()) && config.trades != null && !config.trades.isEmpty()) {
            config.pool = config.trades;
            logger.warn("Loaded legacy schema 1 entries from {}", file.getName());
        }
        if (config.groups.isEmpty() && config.pool != null && !config.pool.isEmpty()) {
            TradeGroupConfig group = new TradeGroupConfig("default");
            group.offerCount = config.offerCount == null ? new IntRange(1) : config.offerCount;
            group.pool = config.pool;
            config.groups.add(group);
            logger.warn("Loaded legacy trade pool from {} as group 'default'", file.getName());
        }
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

    private <T> T read(File file, Class<T> type, T fallback) {
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            T value = GSON.fromJson(reader, type);
            return value == null ? fallback : value;
        } catch (Exception exception) {
            logger.error("Could not read {}; retaining safe defaults", file, exception);
            addDiagnostic(file, cleanMessage(exception));
            return fallback;
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }

    private void addDiagnostic(File file, String reason) {
        loadingDiagnostics.add(file.getName() + ": " + reason);
    }

    private String cleanMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.trim()
            .isEmpty() ? cause.getClass()
                .getSimpleName() : message;
    }

    private void writeIfMissing(File file, Object value) {
        if (!file.exists()) writeReplacing(file, value);
    }

    private void writeReplacing(File file, Object value) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            GSON.toJson(value, writer);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write " + file, exception);
        } finally {
            if (writer != null) try {
                writer.close();
            } catch (IOException ignored) {}
        }
    }
}
