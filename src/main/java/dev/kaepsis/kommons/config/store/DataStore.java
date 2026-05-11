package dev.kaepsis.kommons.config.store;

import dev.kaepsis.kommons.config.core.ConfigContainer;
import dev.kaepsis.kommons.config.parser.IConfigParser;
import dev.kaepsis.kommons.config.parser.impl.YamlParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class DataStore {

    private final ConfigContainer container = new ConfigContainer();
    private final Path path;
    private final IConfigParser parser;

    public DataStore(JavaPlugin plugin, String fileName) {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data folder", e);
        }
        this.path = dataFolder.resolve(fileName);
        this.parser = new YamlParser();
        if (Files.exists(path)) load();
    }

    public void load() {
        try {
            container.replace(parser.load(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path.getFileName(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        try {
            parser.save(path, container.snapshot());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save " + path.getFileName(), e);
        }
    }

    public void set(String key, Object value) {
        container.set(key, value);
        save();
    }

    public void remove(String key) {
        container.remove(key);
        save();
    }

    public Object get(String key) {
        return container.get(key);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return container.getOrDefault(key, defaultValue);
    }

    public String getString(String key) {
        Object v = container.get(key);
        return v != null ? v.toString() : null;
    }

    public boolean hasKey(String key) {
        return container.containsKey(key);
    }

    public long getLong(String key, long def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.longValue() : def;
    }

    public void setLong(String key, long value) {
        container.set(key, value);
        save();
    }

    public void setDouble(String key, double value) {
        container.set(key, value);
        save();
    }

    public double getDouble(String key, double def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public float getFloat(String key, float def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.floatValue() : def;
    }

    public Set<String> getKeys(String path) {
        return container.getKeys(path);
    }
}