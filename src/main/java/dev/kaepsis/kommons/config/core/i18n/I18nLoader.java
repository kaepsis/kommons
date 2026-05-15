package dev.kaepsis.kommons.config.core.i18n;

import dev.kaepsis.kommons.config.annotations.ConfigValue;
import dev.kaepsis.kommons.config.annotations.i18n.LangFile;
import dev.kaepsis.kommons.config.core.ConfigContainer;
import dev.kaepsis.kommons.config.parser.IConfigParser;
import dev.kaepsis.kommons.config.parser.impl.YamlParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and manages internationalized language files for a given locale.
 * <p>
 * This class is responsible for reading YAML language files (e.g., {@code messages_en.yml}),
 * merging user changes with default values, and binding the loaded key‑value pairs into
 * a target class annotated with {@link LangFile}. Fields in the target class must be annotated
 * with {@link ConfigValue} to map a YAML key to the field.
 * </p>
 * <p>
 * The loader supports version checking: if the stored {@code lang-version} is lower than
 * the expected version defined in {@code @LangFile}, the loader merges the user's file with
 * the default resource and updates the version. This allows safe addition of new keys
 * without losing existing customisations.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @LangFile(baseName = "messages", defaultLocale = "en", version = 260514)
 * public class Messages {
 *     @ConfigValue("welcome")
 *     public static String WELCOME = "Welcome!";
 *
 *     @ConfigValue("farewell")
 *     public static String FAREWELL = "Goodbye!";
 * }
 *
 * I18nLoader<Messages> loader = new I18nLoader<>(Messages.class, plugin, "it");
 * loader.load();
 * Messages messages = loader.getInstance();
 * player.sendMessage(messages.WELCOME);  // localized "Benvenuto!"
 * }</pre>
 * </p>
 *
 * @param <T> the type of the language configuration class (annotated with {@link LangFile})
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class I18nLoader<T> {

    private final Class<T> configClass;
    private final JavaPlugin plugin;
    private final IConfigParser parser;
    private final String baseName;
    private final String defaultLocale;
    private final int expectedVersion;

    private String activeLocale;
    private volatile T instance;

    /**
     * Constructs a new I18nLoader for the given language class and initial locale.
     *
     * @param configClass the class that holds the language keys (must be annotated with {@link LangFile})
     * @param plugin      the owning JavaPlugin, used to access resources and the data folder
     * @param locale      the initial locale to load (e.g., {@code "en"}, {@code "it"})
     * @throws IllegalStateException if {@code configClass} is not annotated with {@code @LangFile}
     */
    public I18nLoader(Class<T> configClass, JavaPlugin plugin, String locale) {
        this.configClass = configClass;
        this.plugin = plugin;
        this.parser = new YamlParser();
        LangFile annotation = configClass.getAnnotation(LangFile.class);
        if (annotation == null) {
            throw new IllegalStateException("Missing @LangFile on " + configClass.getName());
        }
        this.baseName = annotation.baseName();
        this.defaultLocale = annotation.defaultLocale();
        this.expectedVersion = annotation.version();
        this.activeLocale = locale;
    }

    /**
     * Returns the loaded language instance (populated with values from the current locale).
     *
     * @return the language instance, or {@code null} if {@link #load()} has not been called yet
     */
    public T getInstance() {
        return instance;
    }

    /**
     * Returns the currently active locale code (e.g., {@code "en"}, {@code "it"}).
     *
     * @return the active locale string
     */
    public String getActiveLocale() {
        return activeLocale;
    }

    /**
     * Loads the language file for the current locale and binds the values into the language class.
     * <p>
     * If the language file does not exist in the plugin's data folder, it is copied from the plugin's
     * resources. User changes are merged with default values, and version handling is applied.
     * </p>
     * <p>
     * After loading, the resulting instance can be retrieved with {@link #getInstance()}.
     * </p>
     */
    public void load() {
        String fileName = resolveFileName(activeLocale);
        Map<String, Object> data = loadFile(fileName);
        instance = bindValues(data);
    }

    /**
     * Reloads the language file for the current locale, discarding any previous instance.
     * Equivalent to calling {@link #load()}.
     */
    public void reload() {
        load();
    }

    /**
     * Switches to a different locale and reloads the language data.
     *
     * @param newLocale the new locale code (e.g., {@code "de"}, {@code "fr"})
     */
    public void switchLocale(String newLocale) {
        this.activeLocale = newLocale;
        load();
    }

    /**
     * Returns a list of all available locales that have corresponding language files
     * in the plugin's data folder.
     * <p>
     * The method scans for files named {@code baseName_locale.yml} (e.g., {@code messages_it.yml})
     * and extracts the locale part. The list is sorted alphabetically.
     * </p>
     *
     * @return a list of locale strings (never {@code null})
     * @throws RuntimeException if an I/O error occurs while scanning the data folder
     */
    public List<String> availableLocales() {
        Path dataFolder = plugin.getDataFolder().toPath();
        String prefix = baseName + "_";

        try (var stream = Files.list(dataFolder)) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".yml"))
                    .map(name -> name.substring(prefix.length(), name.length() - 4))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("[I18n] Failed to scan language files", e);
        }
    }

    /**
     * Loads all available language files and returns a map from locale to language instance.
     * <p>
     * Each language file is loaded independently, and the resulting instances are bound
     * to their own separate objects of type {@code T}. This can be useful for caching
     * all translations at once.
     * </p>
     *
     * @return an ordered {@code Map} where keys are locale codes and values are the corresponding
     *         language instances (never {@code null})
     */
    public Map<String, T> loadAll() {
        Map<String, T> result = new LinkedHashMap<>();
        for (String locale : availableLocales()) {
            String fileName = resolveFileName(locale);
            Map<String, Object> data = loadFile(fileName);
            result.put(locale, bindValues(data));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private String resolveFileName(String locale) {
        String fileName = baseName + "_" + locale + ".yml";
        if (plugin.getResource(fileName) != null) {
            return fileName;
        }
        plugin.getLogger().warning(
                "[I18n] Language file \"" + fileName + "\" not found, falling back to \"" + defaultLocale + "\""
        );
        return baseName + "_" + defaultLocale + ".yml";
    }

    private Map<String, Object> loadFile(String fileName) {
        Path dataFolder = plugin.getDataFolder().toPath();
        Path filePath = dataFolder.resolve(fileName);

        if (!Files.exists(filePath)) {
            InputStream resource = plugin.getResource(fileName);
            if (resource == null) {
                throw new IllegalStateException("[I18n] Resource not found: " + fileName);
            }
            plugin.saveResource(fileName, false);
        }

        Map<String, Object> defaults = loadFromResources(fileName);
        Map<String, Object> user = loadFromDisk(filePath);

        int userVersion = getVersion(user);
        Map<String, Object> merged = merge(defaults, user);

        if (userVersion < expectedVersion) {
            plugin.getLogger().info(
                    "[I18n] Language file \"" + fileName + "\" is outdated (v" + userVersion +
                            " → v" + expectedVersion + "), merging with defaults"
            );
            merged.put("lang-version", expectedVersion);
        } else {
            merged.put("lang-version", userVersion);
        }

        return merged;
    }

    private int getVersion(Map<String, Object> map) {
        Object version = map.get("lang-version");
        if (version instanceof Number) return ((Number) version).intValue();
        return 0;
    }

    private Map<String, Object> loadFromResources(String fileName) {
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) {
                throw new IllegalStateException("[I18n] Resource not found: " + fileName);
            }
            return parser.load(in);
        } catch (IOException e) {
            throw new RuntimeException("[I18n] Failed to load resource: " + fileName, e);
        }
    }

    private Map<String, Object> loadFromDisk(Path path) {
        try {
            return parser.load(path);
        } catch (Exception e) {
            throw new RuntimeException("[I18n] Failed to load file: " + path, e);
        }
    }

    private T bindValues(Map<String, Object> data) {
        try {
            ConfigContainer container = new ConfigContainer();
            container.replace(data);
            T obj = configClass.getDeclaredConstructor().newInstance();
            for (Field field : configClass.getDeclaredFields()) {
                ConfigValue configValue = field.getAnnotation(ConfigValue.class);
                if (configValue == null) continue;
                Object value = container.get(configValue.value());
                if (value == null) {
                    plugin.getLogger().warning("[I18n] Missing key \"" + configValue.value() + "\" in active language file");
                    continue;
                }
                field.setAccessible(true);
                field.set(obj, value.toString());
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("[I18n] Failed to bind language values", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>(defaults);
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            String key = entry.getKey();
            Object userValue = entry.getValue();
            Object defaultValue = defaults.get(key);
            if (userValue instanceof Map && defaultValue instanceof Map) {
                result.put(key, merge(
                        (Map<String, Object>) defaultValue,
                        (Map<String, Object>) userValue
                ));
            } else {
                result.put(key, userValue);
            }
        }
        return result;
    }

}