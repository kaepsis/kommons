package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.parser.IConfigParser;
import dev.kaepsis.kommons.config.parser.impl.YamlParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * A simple key‑value data store backed by a configuration file (YAML by default).
 * <p>
 * {@code DataStore} provides a convenient way to persist arbitrary data using
 * dot‑separated keys (e.g., {@code "players.0.name"}). It automatically loads the
 * file on creation if it exists, and saves after every write operation ({@link #set},
 * {@link #remove}, and the typed setter methods). This makes it suitable for small‑scale
 * data storage such as player data, kit settings, or temporary caches that must survive
 * server restarts.
 * </p>
 * <p>
 * Internally, the store uses a {@link ConfigContainer} to hold data in memory, and an
 * {@link IConfigParser} (YAML) to read from and write to disk. The file is stored in the
 * plugin's data folder under the given file name.
 * </p>
 * <p>
 * All write operations automatically trigger a {@link #save()} to disk. If you need to
 * batch multiple modifications, consider using the underlying {@code ConfigContainer}
 * directly via a snapshot or by calling {@link #load()} after manual edits, but be aware
 * that the store does not support transactions.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * DataStore store = new DataStore(plugin, "playerdata.yml");
 * store.set("player." + uuid + ".name", "Notch");
 * store.set("player." + uuid + ".level", 42);
 * String name = store.getString("player." + uuid + ".name");
 * int level = (int) store.getOrDefault("player." + uuid + ".level", 1);
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class DataStore {

    private final ConfigContainer container = new ConfigContainer();
    private final Path path;
    private final IConfigParser parser;

    /**
     * Creates a new data store backed by the specified file inside the plugin's data folder.
     * <p>
     * If the data folder does not exist, it is created. If the file already exists, it is
     * loaded immediately; otherwise, an empty store is initialised. The store uses YAML
     * format with a {@link YamlParser}.
     * </p>
     *
     * @param plugin   the owning plugin (used to obtain the data folder)
     * @param fileName the name of the file (e.g., {@code "storage.yml"})
     * @throws RuntimeException if the data folder cannot be created or the file cannot be loaded
     */
    public DataStore(JavaPlugin plugin, String fileName) {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data folder", e);
        }
        this.path = dataFolder.resolve(fileName);
        this.parser = ConfigParsers.forFile(fileName);
        if (Files.exists(path)) load();
    }

    /**
     * Loads the data from the file into memory.
     * <p>
     * This replaces the current in‑memory content with what is on disk. Any unsaved
     * changes will be lost. The method is automatically called during construction if
     * the file exists, but can also be used to manually reload (e.g., after external edits).
     * </p>
     *
     * @throws RuntimeException if loading fails (I/O or parsing error)
     */
    public void load() {
        try {
            container.replace(parser.load(path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + path.getFileName(), e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Saves the current in‑memory data to the file.
     * <p>
     * This method is automatically called after every write operation ({@link #set},
     * {@link #remove}, and the typed setters). You rarely need to call it explicitly
     * unless you have modified the underlying {@code ConfigContainer} via other means.
     * </p>
     *
     * @throws RuntimeException if saving fails (I/O or serialisation error)
     */
    public void save() {
        try {
            parser.save(path, container.snapshot());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save " + path.getFileName(), e);
        }
    }

    /**
     * Stores a value at the given dot‑separated key and saves the file.
     * <p>
     * If intermediate keys do not exist, they are created as nested maps. The value can
     * be any object supported by the underlying parser (primitives, strings, lists, maps).
     * </p>
     *
     * @param key   the dot‑separated key (e.g., {@code "user.preferences.theme"})
     * @param value the value to store (may be {@code null})
     */
    public void set(String key, Object value) {
        container.set(key, value);
        save();
    }

    /**
     * Removes the entry at the given key and saves the file.
     * <p>
     * If the key does not exist, this method does nothing. Nested parent maps are
     * not automatically cleaned up (empty maps remain).
     * </p>
     *
     * @param key the dot‑separated key to remove
     */
    public void remove(String key) {
        container.remove(key);
        save();
    }

    /**
     * Returns the raw value associated with the given key.
     *
     * @param key the dot‑separated key
     * @return the stored value, or {@code null} if the key does not exist
     */
    public Object get(String key) {
        return container.get(key);
    }

    /**
     * Returns the value for the given key, or a default if not present.
     *
     * @param key          the dot‑separated key
     * @param defaultValue the value to return if the key is not found
     * @return the stored value, or {@code defaultValue} if the key does not exist
     */
    public Object getOrDefault(String key, Object defaultValue) {
        return container.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the value for the given key as a string.
     *
     * @param key the dot‑separated key
     * @return the string representation of the value, or {@code null} if the key does not exist
     */
    public String getString(String key) {
        Object v = container.get(key);
        return v != null ? v.toString() : null;
    }

    /**
     * Checks whether the given key exists in the store.
     *
     * @param key the dot‑separated key
     * @return {@code true} if the key is present, {@code false} otherwise
     */
    public boolean hasKey(String key) {
        return container.containsKey(key);
    }

    /**
     * Returns the value for the given key as a long, or a default if the key is missing
     * or not a number.
     *
     * @param key the dot‑separated key
     * @param def the default value
     * @return the stored number as a long, or {@code def} if the key does not exist or is not numeric
     */
    public long getLong(String key, long def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.longValue() : def;
    }

    /**
     * Stores a long value at the given key and saves the file.
     *
     * @param key   the dot‑separated key
     * @param value the long value
     */
    public void setLong(String key, long value) {
        container.set(key, value);
        save();
    }

    /**
     * Stores a double value at the given key and saves the file.
     *
     * @param key   the dot‑separated key
     * @param value the double value
     */
    public void setDouble(String key, double value) {
        container.set(key, value);
        save();
    }

    /**
     * Returns the value for the given key as a double, or a default if the key is missing
     * or not a number.
     *
     * @param key the dot‑separated key
     * @param def the default value
     * @return the stored number as a double, or {@code def} if the key does not exist or is not numeric
     */
    public double getDouble(String key, double def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * Returns the value for the given key as a float, or a default if the key is missing
     * or not a number.
     *
     * @param key the dot‑separated key
     * @param def the default value
     * @return the stored number as a float, or {@code def} if the key does not exist or is not numeric
     */
    public float getFloat(String key, float def) {
        Object v = container.get(key);
        return v instanceof Number n ? n.floatValue() : def;
    }

    /**
     * Returns all direct child keys under the given path.
     * <p>
     * For example, if the store contains:
     * <pre>{@code
     * players:
     *   alice:
     *     level: 10
     *   bob:
     *     level: 20
     * }</pre>
     * then {@code getKeys("players")} returns {@code ["alice", "bob"]}.
     * </p>
     *
     * @param path the dot‑separated path to the parent map; if {@code null} or empty,
     *             returns the keys at the root level
     * @return a set of keys under the given path (never {@code null})
     */
    public Set<String> getKeys(String path) {
        return container.getKeys(path);
    }
}