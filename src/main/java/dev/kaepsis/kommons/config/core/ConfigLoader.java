package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.annotations.ConfigFile;
import dev.kaepsis.kommons.config.annotations.ConfigValue;
import dev.kaepsis.kommons.config.parser.IConfigParser;
import dev.kaepsis.kommons.config.parser.impl.YamlParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads, saves, and manages a configuration file for a given configuration class.
 * <p>
 * {@code ConfigLoader} is responsible for synchronising a YAML configuration file
 * (or any format supported by an {@link IConfigParser}) with a Java class annotated
 * with {@link ConfigFile}. Fields in that class that should be mapped to configuration
 * keys must be annotated with {@link ConfigValue}.
 * </p>
 * <p>
 * The loader performs version‑aware merging: if the {@code config-version} stored in
 * the user's file is lower than the version declared in {@code @ConfigFile.version()},
 * the loader merges the user's values with the plugin's default configuration
 * (embedded as a resource) and updates the version. This allows safe addition of
 * new keys without losing existing customisations.
 * </p>
 * <p>
 * The loader uses a {@link ConfigContainer} as the in‑memory representation and
 * automatically converts primitive types, strings, maps, and even nested objects
 * (when a field is of a custom class that appears as a map in the configuration).
 * </p>
 * <p>
 * All changes can be persisted via {@link #save()}, and the configuration can be
 * reloaded from disk via {@link #reload()}. Individual key‑value pairs can be
 * updated programmatically with {@link #setValue(String, Object)} which also
 * persists the change.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @ConfigFile(name = "settings.yml", version = 260514)
 * public class Settings {
 *     @ConfigValue("server.name")
 *     public static String SERVER_NAME = "MyServer";
 *
 *     @ConfigValue("server.port")
 *     public static int PORT = 25565;
 *
 *     @ConfigValue("database")
 *     public static DatabaseConfig DATABASE = new DatabaseConfig();
 * }
 *
 * ConfigLoader<Settings> loader = new ConfigLoader<>(Settings.class, plugin);
 * Settings config = loader.load();
 * // config.SERVER_NAME is now the value from the YAML file
 * }</pre>
 * </p>
 *
 * @param <T> the type of the configuration class (annotated with {@link ConfigFile})
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class ConfigLoader<T> {

    private final Class<T> configClass;
    private final ConfigContainer container = new ConfigContainer();
    private final JavaPlugin plugin;
    private Path path;
    private IConfigParser parser;
    private volatile T instance;
    private int expectedVersion;

    /**
     * Creates a new configuration loader for the given class and plugin.
     * <p>
     * The constructor validates the presence of {@code @ConfigFile} and ensures
     * the data folder exists. If the configuration file does not exist yet,
     * the default resource (with the same name as declared in {@code @ConfigFile.name()})
     * is copied to the plugin's data folder.
     * </p>
     *
     * @param configClass the class that holds the configuration fields (must be annotated with {@link ConfigFile})
     * @param plugin      the owning JavaPlugin, used to access the data folder and resources
     * @throws IllegalStateException if {@code configClass} is not annotated with {@code @ConfigFile}
     * @throws RuntimeException      if the data folder cannot be created
     */
    public ConfigLoader(Class<T> configClass, JavaPlugin plugin) {
        this.configClass = configClass;
        this.plugin = plugin;
        init();
    }

    /**
     * Returns the loaded configuration instance.
     *
     * @return the configuration instance (populated with values from the file),
     *         or {@code null} if {@link #load()} has not been called yet
     */
    public T getInstance() {
        return instance;
    }

    private void init() {
        ConfigFile configFile = configClass.getAnnotation(ConfigFile.class);
        if (configFile == null) {
            throw new IllegalStateException("Missing @ConfigFile on " + configClass.getName());
        }
        this.expectedVersion = configFile.version();

        Path dataFolder = plugin.getDataFolder().toPath();
        if (!Files.exists(dataFolder)) {
            try {
                Files.createDirectories(dataFolder);
            } catch (IOException e) {
                throw new RuntimeException("Impossible to create data folder: " + e.getMessage());
            }
        }
        this.path = dataFolder.resolve(configFile.name());
        this.parser = new YamlParser();
        if (!Files.exists(path)) {
            plugin.saveResource(configFile.name(), false);
        }
    }

    private Map<String, Object> loadDefaultMap() throws IOException {
        String fileName = configClass.getAnnotation(ConfigFile.class).name();
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                throw new IOException("Default config resource not found: " + fileName);
            }
            return parser.load(in);
        }
    }

    /**
     * Loads the configuration file, merges with defaults, and returns the populated instance.
     * <p>
     * The loading process follows these steps:
     * <ol>
     *   <li>Read the default configuration from the plugin's resources.</li>
     *   <li>Read the user configuration from the disk (if it exists).</li>
     *   <li>Merge them, giving priority to user values while preserving the order of keys.</li>
     *   <li>If the stored {@code config-version} is lower than the expected version,
     *       the merged result is updated with the expected version and any missing keys from defaults.</li>
     *   <li>The merged data is stored in the internal {@link ConfigContainer}.</li>
     *   <li>A new instance of {@code T} is created and its {@code @ConfigValue} fields are
     *       populated with the values from the container.</li>
     * </ol>
     * </p>
     *
     * @return the fully populated configuration instance
     * @throws RuntimeException if loading or binding fails
     */
    public T load() {
        try {
            Map<String, Object> defaults = loadDefaultMap();
            Map<String, Object> user = Files.exists(path) ? parser.load(path) : new LinkedHashMap<>();

            Map<String, Object> merged = mergePreservingOrder(defaults, user);
            int userVersion = getVersion(user);

            if (userVersion < expectedVersion) {
                merged = mergePreservingOrder(defaults, user);
                merged.put("config-version", expectedVersion);
            } else {
                merged.put("config-version", userVersion);
            }

            container.replace(merged);
            instance = bindValues();
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    /**
     * Saves the current in‑memory configuration to disk.
     * <p>
     * The entire snapshot of the {@link ConfigContainer} is written to the file
     * using the configured parser (YAML by default). This method is automatically
     * called by {@link #setValue(String, Object)} but can also be used explicitly.
     * </p>
     *
     * @throws RuntimeException if the file cannot be written
     */
    public void save() {
        try {
            parser.save(path, container.snapshot());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reloads the configuration from disk and updates the existing instance.
     * <p>
     * Unlike {@link #load()} which creates a new instance, {@code reload()} reads
     * the current file (and defaults), merges with version handling, and then
     * updates the fields of the existing instance (the one previously returned
     * by {@link #getInstance()}). This is useful for hot‑reloading without
     * losing references to the configuration object.
     * </p>
     *
     * @throws RuntimeException if reloading or binding fails
     */
    public void reload() {
        try {
            Map<String, Object> defaults = loadDefaultMap();
            Map<String, Object> user = parser.load(path);
            int userVersion = getVersion(user);

            Map<String, Object> merged;
            if (userVersion < expectedVersion) {
                merged = mergePreservingOrder(defaults, user);
                merged.put("config-version", expectedVersion);
            } else {
                merged = mergePreservingOrder(defaults, user);
                merged.put("config-version", userVersion);
            }

            container.replace(merged);
            bindInto(instance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload config", e);
        }
    }

    /**
     * Sets a value for a specific configuration key, updates the in‑memory
     * representation, and saves the file.
     * <p>
     * The method updates the underlying {@link ConfigContainer}, rebinds all
     * values into the configuration instance (so that static/instance fields
     * reflect the new value), and then calls {@link #save()} to persist the change.
     * </p>
     *
     * @param key   the dot‑separated configuration key (e.g., {@code "server.port"})
     * @param value the new value to store
     * @throws RuntimeException if binding or saving fails
     */
    public void setValue(String key, Object value) {
        container.set(key, value);
        try {
            instance = bindValues();
        } catch (Exception e) {
            throw new RuntimeException("Error while binding values: " + e.getMessage());
        }
        save();
    }

    private T bindValues() throws Exception {
        T instance = configClass.getDeclaredConstructor().newInstance();
        for (Field field : configClass.getDeclaredFields()) {
            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue == null) continue;
            Object value = container.get(configValue.value());
            field.setAccessible(true);
            field.set(instance, convertField(value, field));
        }
        return instance;
    }

    private Object convertField(Object value, Field field) throws Exception {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            if (Map.class.isAssignableFrom(rawType) && value instanceof Map<?, ?> rawMap) {
                Type valueType = pt.getActualTypeArguments()[1];
                if (valueType instanceof Class<?> valueClass
                        && valueClass != String.class
                        && valueClass != Object.class) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        String k = entry.getKey().toString();
                        if (entry.getValue() instanceof Map<?, ?> nested) {
                            Map<String, Object> nestedMap = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> ne : nested.entrySet())
                                nestedMap.put(ne.getKey().toString(), ne.getValue());
                            result.put(k, bindObjectFromMap(nestedMap, valueClass));
                        }
                    }
                    return result;
                }
            }
        }
        return convert(value, field.getType());
    }

    private void bindInto(T target) throws Exception {
        for (Field field : configClass.getDeclaredFields()) {
            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue == null) continue;
            String key = configValue.value();
            Object value = container.get(key);
            if (value == null) {
                throw new IllegalStateException("Missing config key: " + key);
            }
            field.setAccessible(true);
            field.set(target, convertField(value, field));
        }
    }

    private Object convert(Object value, Class<?> type) throws Exception {
        if (value == null) return null;
        if (type.isPrimitive()) {
            if (type == int.class) {
                if (value instanceof Number) return ((Number) value).intValue();
                return Integer.parseInt(value.toString());
            }
            if (type == boolean.class) {
                if (value instanceof Boolean) return value;
                return Boolean.parseBoolean(value.toString());
            }
            if (type == double.class) {
                if (value instanceof Number) return ((Number) value).doubleValue();
                return Double.parseDouble(value.toString());
            }
            if (type == float.class) {
                if (value instanceof Number) return ((Number) value).floatValue();
                return Float.parseFloat(value.toString());
            }
            if (type == long.class) {
                if (value instanceof Number) return ((Number) value).longValue();
                return Long.parseLong(value.toString());
            }
            if (type == short.class) {
                if (value instanceof Number) return ((Number) value).shortValue();
                return Short.parseShort(value.toString());
            }
            if (type == byte.class) {
                if (value instanceof Number) return ((Number) value).byteValue();
                return Byte.parseByte(value.toString());
            }
            return null;
        }
        if (type.isInstance(value)) return value;
        if (type == String.class) return value.toString();
        if (type == Map.class) {
            if (value instanceof Map<?, ?>) return value;
            throw new IllegalArgumentException("Expected Map but got: " + value.getClass());
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> safeMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String))
                    throw new IllegalArgumentException("Key is not String: " + entry.getKey());
                safeMap.put(entry.getKey().toString(), entry.getValue());
            }
            return bindObjectFromMap(safeMap, type);
        }
        throw new IllegalArgumentException("Cannot convert " + value + " (" + value.getClass() + ") to " + type);
    }

    private <R> R bindObjectFromMap(Map<String, Object> map, Class<R> type) throws Exception {
        R instance = type.getDeclaredConstructor().newInstance();
        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);
            Object raw = map.get(field.getName());
            if (raw == null) continue;
            field.set(instance, convert(raw, field.getType()));
        }
        return instance;
    }

    private int getVersion(Map<String, Object> map) {
        Object version = map.get("config-version");
        if (version instanceof Number) {
            return ((Number) version).intValue();
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergePreservingOrder(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            String key = entry.getKey();
            Object userValue = entry.getValue();
            if (defaults.containsKey(key)) {
                Object defaultValue = defaults.get(key);
                if (userValue instanceof Map && defaultValue instanceof Map) {
                    Map<String, Object> dv = (Map<String, Object>) defaultValue;
                    Map<String, Object> uv = (Map<String, Object>) userValue;
                    result.put(key, mergePreservingOrder(dv, uv));
                } else {
                    result.put(key, userValue);
                }
            } else {
                result.put(key, userValue);
            }
        }
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            if (!result.containsKey(key)) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }
}