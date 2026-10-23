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
