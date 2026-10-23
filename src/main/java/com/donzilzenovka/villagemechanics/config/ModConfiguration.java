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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ModConfiguration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private final File root;
    private final File tradesDirectory;
    private final File generatedDefaultsDirectory;
    private final File combatFile;
    private final Logger logger;
    private volatile CombatConfig combat = new CombatConfig();
    private volatile Map<Integer, ProfessionTradeConfig> trades = Collections.emptyMap();

    public ModConfiguration(File root, Logger logger) {
        this.root = root;
        this.tradesDirectory = new File(root, "trades");
        this.generatedDefaultsDirectory = new File(root, "generated_defaults");
        this.combatFile = new File(root, "combat.json");
        this.logger = logger;
    }

    public synchronized void load() {
        ensureDirectories();
        writeIfMissing(combatFile, DefaultConfigurations.createCombatDefault());

        combat = read(combatFile, CombatConfig.class, DefaultConfigurations.createCombatDefault());
        trades = Collections.unmodifiableMap(readTradeFiles());
        logger.info("Loaded {} profession trade configurations", trades.size());
    }

    public synchronized void discoverProfessions(Iterable<Integer> professions) {
        boolean added = false;
        for (Integer profession : professions) {
            if (profession == null || profession.intValue() < 0 || trades.containsKey(profession)) continue;
            int id = profession.intValue();
            String name = DefaultConfigurations.fileName(id);
            File active = new File(tradesDirectory, name);
            if (active.exists()) continue;

            ProfessionTradeConfig config = DefaultConfigurations.createDiscoveredProfessionDefault(id);
            writeReplacing(active, config);
            writeReplacing(new File(generatedDefaultsDirectory, name), config);
            logger.info("Created safe append template {} for registered profession {}", name, id);
            added = true;
        }
        if (added) load();
    }

    public synchronized String discoverProfession(int profession) {
        if (profession < 0 || trades.containsKey(Integer.valueOf(profession))) return null;

        ensureDirectories();
        ProfessionTradeConfig config = DefaultConfigurations.createDiscoveredProfessionDefault(profession);
        String name = DefaultConfigurations.fileName(profession);
        File active = new File(tradesDirectory, name);
        boolean created = !active.exists();
        writeIfMissing(active, config);
        writeReplacing(new File(generatedDefaultsDirectory, name), config);
        load();

        if (created && trades.containsKey(Integer.valueOf(profession))) {
            logger.info("Created safe append template {} for inspected profession {}", name, profession);
            return name;
        }
        return null;
    }

    public synchronized int[] recordObservedTrades(int profession, List<WeightedTrade> observed) {
        ensureDirectories();
        String name = DefaultConfigurations.fileName(profession);
        File reference = new File(generatedDefaultsDirectory, name);
        ProfessionTradeConfig catalogue = read(reference, ProfessionTradeConfig.class, null);
        if (catalogue == null) catalogue = DefaultConfigurations.createDiscoveredProfessionDefault(profession);
        if (catalogue.trades == null) catalogue.trades = new ArrayList<WeightedTrade>();

        Map<String, WeightedTrade> unique = new LinkedHashMap<String, WeightedTrade>();
        for (WeightedTrade trade : catalogue.trades) {
            if (trade != null && trade.buy != null && trade.sell != null) unique.put(tradeKey(trade), trade);
        }

        int before = unique.size();
        for (WeightedTrade trade : observed) {
            if (trade != null && trade.buy != null && trade.sell != null) {
                String key = tradeKey(trade);
                if (!unique.containsKey(key)) unique.put(key, trade);
            }
        }

        catalogue.profession = profession;
        catalogue.mode = "replace";
        catalogue.trades = new ArrayList<WeightedTrade>(unique.values());
        catalogue.rolls = catalogue.trades.size();
        for (int index = 0; index < catalogue.trades.size(); index++) {
            catalogue.trades.get(index).id = "observed_" + (index + 1);
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

    private void ensureDirectories() {
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Could not create " + root);
        }
        if (!tradesDirectory.isDirectory() && !tradesDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create " + tradesDirectory);
        }
        if (!generatedDefaultsDirectory.isDirectory() && !generatedDefaultsDirectory.mkdirs()) {
            throw new IllegalStateException("Could not create " + generatedDefaultsDirectory);
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
            if (config == null || !validate(config, file)) continue;
            ProfessionTradeConfig replaced = result.put(Integer.valueOf(config.profession), config);
            if (replaced != null) {
                logger.warn("{} overrides another file for profession {}", file.getName(), config.profession);
            }
        }
        return result;
    }

    private boolean validate(ProfessionTradeConfig config, File file) {
        if (!("replace".equalsIgnoreCase(config.mode) || "append".equalsIgnoreCase(config.mode))) {
            logger.error("Ignoring {}: mode must be replace or append", file.getName());
            return false;
        }
        if (config.rolls < 0) {
            logger.error("Ignoring {}: rolls cannot be negative", file.getName());
            return false;
        }
        if (config.trades == null) config.trades = new ArrayList<WeightedTrade>();
        return true;
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
            return fallback;
        } finally {
            if (reader != null) try {
                reader.close();
            } catch (IOException ignored) {}
        }
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
