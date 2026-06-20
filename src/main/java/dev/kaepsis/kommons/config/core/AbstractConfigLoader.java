package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.parser.IConfigParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A package-private abstract base class shared by concrete configuration loader implementations,
 * such as {@link ConfigLoader} and {@link I18nLoader}.
 * <p>
 * This class orchestrates the shared underlying state, file I/O operations, and data structures
 * required to manage configuration lifecycle routines. The format-specific {@link IConfigParser}
 * strategy is resolved by subclasses during instantiation and injected dynamically into this layer.
 * </p>
 * <p>
 * Key shared features provided by this layer include:
 * <ul>
 * <li>Disk-based reading, writing, and platform data directory preservation utilities.</li>
 * <li>Embedded JAR resource stream loading routines.</li>
 * <li>A deep, recursive mapping fallback merge strategy to preserve user-customized keys
 * while populating missing defaults from upstream updates.</li>
 * </ul>
 * </p>
 *
 * @param <T> the type of the structured domain configuration object instance mapped by this loader
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
abstract class AbstractConfigLoader<T> {

    /** The runtime configuration model blueprint class definition. */
    protected final Class<T> configClass;

    /** The hosting {@link JavaPlugin} platform execution context instance. */
    protected final JavaPlugin plugin;

    /** The active strategy format parser implementation used to serialize or deserialize files. */
    protected final IConfigParser parser;

    /** The cached, live runtime configuration mapping domain instance model. */
    protected volatile T instance;

    /**
     * Internal constructor for subclasses to initialize shared configuration parameters.
     *
     * @param configClass the metadata configuration {@link Class} container blueprint type
     * @param plugin      the hosting {@link JavaPlugin} platform runtime execution context instance
     * @param parser      the dedicated {@link IConfigParser} format handler assigned to this asset
     */
    AbstractConfigLoader(Class<T> configClass, JavaPlugin plugin, IConfigParser parser) {
        this.configClass = configClass;
        this.plugin = plugin;
        this.parser = parser;
    }

    /**
     * Returns the currently loaded, thread-safe live instance wrapper representing the state
     * of the configuration mapping properties.
     *
     * @return the active configuration model object instance, or {@code null} if it has not yet been loaded
     */
    public T getInstance() { return instance; }

    /**
     * Triggers a synchronous pass over the configuration file to load, validate, merge,
     * and initialize the live target reference model instance state variables.
     */
    public abstract void load();

    /**
     * Refreshes and updates the active configuration context state parameters by force re-running
     * structural parser data pipelines against filesystem resources.
     */
    public abstract void reload();

    // -------------------------------------------------------------------------
    // Shared I/O
    // -------------------------------------------------------------------------

    /**
     * Safely reads a structural attributes tree map directly out of a filesystem storage file location.
     * <p>
     * If the specified target storage file does not yet exist on disk, this method safely aborts the operation
     * and returns an empty map structure to preserve downstream processing pipelines.
     * </p>
     *
     * @param path the objective file destination {@link Path} context to read from
     * @return a {@link Map} tracking the parsed structured attributes tree data properties found on disk
     * @throws RuntimeException if the underlying format parser routine fails or faces general I/O block errors
     */
    protected Map<String, Object> loadFromDisk(Path path) {
        try {
            return Files.exists(path) ? parser.load(path) : new LinkedHashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load from disk: " + path, e);
        }
    }

    /**
     * Extracts and processes a pristine defaults schema mapping tree out of an embedded file stream
     * bundled directly inside the application's distribution JAR artifact.
     *
     * @param fileName the relative filename or resource path inside the JAR archive
     * @return a {@link Map} tracking the parsed baseline layout defaults configuration tree properties
     * @throws RuntimeException if the resource stream cannot be located or parsed successfully
     */
    protected Map<String, Object> loadFromResources(String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) throw new IOException("Resource not found: " + fileName);
            return parser.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + fileName, e);
        }
    }

    /**
     * Dispatches a structured attributes metadata tree down to the file parser implementation
     * to execute serializations directly onto targeted disk file storage contexts.
     *
     * @param path the objective target destination filesystem {@link Path}
     * @param data the attributes tree configuration map data parameters to commit
     * @throws RuntimeException if an I/O blocking fault or formatting issue prevents serialization
     */
    protected void saveToDisk(Path path, Map<String, Object> data) {
        try {
            parser.save(path, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save: " + path, e);
        }
    }

    /**
     * Assures structural directories mapping to the hosting platform's specific data storage namespace
     * are fully initialized on the filesystem, creating missing directory segments automatically.
     *
     * @throws RuntimeException if host directory authorizations or file collision locks disrupt file operations
     */
    protected void ensureDataFolder() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data folder", e);
        }
    }

    // -------------------------------------------------------------------------
    // Shared logic
    // -------------------------------------------------------------------------

    /**
     * Extracts an integer schema version indicator flag parameter out of a configuration map tree context.
     *
     * @param map        the target attributes map branch to evaluate
     * @param versionKey the designated configuration node string key identifying the schema version property
     * @return the integer version value specified by the configuration block node, or {@code 0} if absent or malformed
     */
    protected int getVersion(Map<String, Object> map, String versionKey) {
        Object v = map.get(versionKey);
        return v instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Deeply merges a default fallback layout map with user-modified custom configurations in a recursive fashion.
     * <p>
     * This method copies the baseline defaults layout map entirely, then iterates over user-defined overrides:
     * <ul>
     * <li>If both the default value and user value corresponding to a key are sub-maps, they are recursively merged.</li>
     * <li>Otherwise, the user-defined value safely overwrites the default mapping value.</li>
     * </ul>
     * Insertion order of keys is preserved across operations through the continuous use of {@link LinkedHashMap}.
     * </p>
     *
     * @param defaults the baseline upstream layout properties template map structure source
     * @param user     the user configuration metadata mapping tree containing customized modifications
     * @return a standalone unified {@link Map} tracking the fully merged results
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            String key = entry.getKey();
            Object uv = entry.getValue();
            Object dv = defaults.get(key);
            if (uv instanceof Map && dv instanceof Map) {
                result.put(key, merge((Map<String, Object>) dv, (Map<String, Object>) uv));
            } else {
                result.put(key, uv);
            }
        }
        return result;
    }
}