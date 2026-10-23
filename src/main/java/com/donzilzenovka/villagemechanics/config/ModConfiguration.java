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
        List<ProfessionTradeConfig> defaults = DefaultConfigurations.createTradeDefaults();
        for (ProfessionTradeConfig config : defaults) {
            String name = DefaultConfigurations.fileName(config.profession);
            writeIfMissing(new File(tradesDirectory, name), config);
            writeReplacing(new File(generatedDefaultsDirectory, name), config);
        }
        writeIfMissing(combatFile, DefaultConfigurations.createCombatDefault());

        combat = read(combatFile, CombatConfig.class, DefaultConfigurations.createCombatDefault());
        trades = Collections.unmodifiableMap(readTradeFiles());
        logger.info("Loaded {} profession trade configurations", trades.size());
    }

    public synchronized void discoverProfessions(Iterable<Integer> professions) {
        ensureDirectories();
        Set<Integer> discovered = new HashSet<Integer>();
        for (Integer profession : professions) {
            if (profession != null && profession.intValue() >= 0) discovered.add(profession);
        }
        boolean added = false;
        for (Integer profession : discovered) {
            int id = profession.intValue();
            if (trades.containsKey(profession)) continue;
            ProfessionTradeConfig config = DefaultConfigurations.createDiscoveredProfessionDefault(id);
            String name = DefaultConfigurations.fileName(id);
            File active = new File(tradesDirectory, name);
            writeIfMissing(active, config);
            writeReplacing(new File(generatedDefaultsDirectory, name), config);
            added = true;
            logger.info("Discovered villager profession {}; created safe append template {}", id, active.getName());
        }
        if (added) load();
    }

    public synchronized void discoverProfession(int profession) {
        discoverProfessions(Collections.singleton(Integer.valueOf(profession)));
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
        if (config.remove == null) config.remove = new ArrayList<TradeRemoval>();
        for (TradeRemoval removal : config.remove) {
            if (removal == null || !validRemoval(removal)) {
                logger.error(
                    "Ignoring {}: each remove entry and item object must contain at least one match field",
                    file.getName());
                return false;
            }
        }
        if (config.trades == null) config.trades = new ArrayList<WeightedTrade>();
        return true;
    }

    private boolean validRemoval(TradeRemoval removal) {
        boolean present = removal.buy != null || removal.buySecondary != null || removal.sell != null;
        return present && validMatch(removal.buy) && validMatch(removal.buySecondary) && validMatch(removal.sell);
    }

    private boolean validMatch(ItemMatch match) {
        return match == null || match.meta != null
            || match.count != null
            || (match.item != null && !match.item.trim()
                .isEmpty());
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
