package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.annotations.ConfigValue;
import dev.kaepsis.kommons.config.annotations.i18n.LangFile;
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
 * Loads, manages, and maps internationalization (i18n) language configurations for a target class
 * annotated with {@link LangFile}.
 * <p>
 * This class handles standard localization lifecycle workflows including absolute runtime locale
 * switching, seamless fallback behavior to a configured default locale if a file is missing,
 * and version-aware file updates across platform migrations.
 * </p>
 * <p>
 * Unlike a generalized config loader, all deserialized values processed by this loader are string-bound
 * directly onto fields using {@link Object#toString()} mapping logic. If data type casting or complex nested
 * objects are required within configuration data fields, utilize a specialized {@code ConfigLoader} implementation instead.
 * </p>
 *
 * @param <T> the type of the domain translation configuration container class managed by this loader
 * @see AbstractConfigLoader
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class I18nLoader<T> extends AbstractConfigLoader<T> {

    /** The internal structural version mapping identifier property key used inside configuration files. */
    private static final String VERSION_KEY = "lang-version";

    /**
     * Resolves a temporary sample filename configuration mapping rule driven by metadata elements
     * before the core super-constructor initializes the backing file format parser pipeline.
     */
    private static String resolveExtension(Class<?> configClass, String locale) {
        LangFile meta = configClass.getAnnotation(LangFile.class);
        if (meta == null) throw new IllegalStateException("Missing @LangFile on " + configClass.getSimpleName());
        // use locale to build a sample filename — extension drives parser choice
        return meta.baseName() + "_" + locale + ".yml"; // override to .toml if needed
    }

    private final String baseName;
    private final String defaultLocale;
    private final int expectedVersion;
    private String activeLocale;

    /**
     * Constructs a new {@code I18nLoader} instance linked to a specific localization context.
     * <p>
     * Scans and verifies the required {@link LangFile} annotation properties on the class definition
     * metadata tree, configures the parent file translation mechanics, and ensures that the base
     * platform data directory is initialized.
     * </p>
     *
     * @param configClass the metadata configuration {@link Class} container blueprint type
     * @param plugin      the hosting {@link JavaPlugin} platform runtime execution context instance
     * @param locale      the primary target configuration locale identifier (e.g., "en", "it", "fr")
     * @throws IllegalStateException if the target configuration container class is missing the {@link LangFile} annotation
     */
    public I18nLoader(Class<T> configClass, JavaPlugin plugin, String locale) {
        super(configClass, plugin, ConfigParsers.forFile(
                resolveExtension(configClass, locale)
        ));

        LangFile meta = configClass.getAnnotation(LangFile.class);
        if (meta == null) throw new IllegalStateException("Missing @LangFile on " + configClass.getSimpleName());

        this.baseName = meta.baseName();
        this.defaultLocale = meta.defaultLocale();
        this.expectedVersion = meta.version();
        this.activeLocale = locale;

        ensureDataFolder();
    }

    /**
     * Retrieves the currently active configuration locale identifier string.
     *
     * @return the active locale identifier tag string
     */
    public String getActiveLocale() {
        return activeLocale;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Resolves the proper localized language filename, loads file hierarchies from disk or JAR contexts,
     * and maps reflection parameters directly onto the internal configuration object reference context.
     * </p>
     */
    @Override
    public void load() {
        String fileName = resolveFileName(activeLocale);
        Map<String, Object> data = loadAndMerge(fileName);
        instance = bindValues(data);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Re-runs the localization mapping layer workflow to refresh state variables from modified files.
     * </p>
     */
    @Override
    public void reload() {
        load();
    }

    /**
     * Swaps the active translation layer to a completely new localization target,
     * explicitly triggering a new execution pass over the asset file parser pipeline.
     *
     * @param locale the target locale identifier tag string to switch to
     */
    public void switchLocale(String locale) {
        this.activeLocale = locale;
        load();
    }

    /**
     * Scans the local application filesystem space to compile an ordered list of all available
     * translation file locales matching the structural base path identity rules.
     *
     * @return a sorted {@link List} of short locale strings (e.g., {@code ["en", "fr", "it"]})
     * @throws RuntimeException if an I/O exception occurs while exploring files inside the base directory
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
     * Simultaneously loads, validates, and initializes all available translation structures discovered
     * on the host file storage framework.
     *
     * @return an ordered {@link Map} tracking fully-mapped concrete container instances indexed by their locale tag strings
     */
    public Map<String, T> loadAll() {
        Map<String, T> result = new LinkedHashMap<>();
        for (String locale : availableLocales()) {
            String fileName = resolveFileName(locale);
            result.put(locale, bindValues(loadAndMerge(fileName)));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    /**
     * Resolves the definitive structural file name path mapping for a given target locale indicator string.
     * <p>
     * If the specified filename does not correspond to an internal file embedded within the JAR assets framework,
     * the system issues a warning log entry and falls back to the preset default file template syntax structure.
     * </p>
     */
    private String resolveFileName(String locale) {
        String fileName = baseName + "_" + locale + ".yml";
        if (plugin.getResource(fileName) != null) return fileName;
        plugin.getLogger().warning("[I18n] Locale '" + locale + "' not found, falling back to '" + defaultLocale + "'");
        return baseName + "_" + defaultLocale + ".yml";
    }

    /**
     * Extracts, validates, updates, and merges structural key-value pairing maps across internal assets and disk-level systems.
     * <p>
     * If no configuration file exists at the host path destinations, the system attempts to copy out a pristine version
     * template block directly out of its embedded resource stream. Furthermore, if version identifiers are outdated, the method
     * performs schema updates and saves formatting layers back onto disk storage safely.
     * </p>
     */
    private Map<String, Object> loadAndMerge(String fileName) {
        Path filePath = plugin.getDataFolder().toPath().resolve(fileName);
        if (!Files.exists(filePath)) {
            InputStream resource = plugin.getResource(fileName);
            if (resource == null) throw new IllegalStateException("[I18n] Resource not found: " + fileName);
            plugin.saveResource(fileName, false);
        }

        Map<String, Object> defaults = loadFromResources(fileName);
        Map<String, Object> user = loadFromDisk(filePath);
        Map<String, Object> merged = merge(defaults, user);

        int storedVersion = getVersion(user, VERSION_KEY);
        if (storedVersion < expectedVersion) {
            plugin.getLogger().info("[I18n] '" + fileName + "' outdated (v" + storedVersion + " → v" + expectedVersion + "), merging");
        }
        merged.put(VERSION_KEY, Math.max(storedVersion, expectedVersion));

        return merged;
    }

    /**
     * Reflectively instantiates a target object type block and maps raw string content properties
     * down onto target field objects declaring the {@link ConfigValue} structural metadata markers.
     */
    private T bindValues(Map<String, Object> data) {
        try {
            T obj = configClass.getDeclaredConstructor().newInstance();
            for (Field field : configClass.getDeclaredFields()) {
                ConfigValue cv = field.getAnnotation(ConfigValue.class);
                if (cv == null) continue;
                Object value = data.get(cv.value());
                if (value == null) {
                    plugin.getLogger().warning("[I18n] Missing key '" + cv.value() + "' in " + activeLocale);
                    continue;
                }
                field.setAccessible(true);
                field.set(obj, value.toString());
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("[I18n] Failed to bind values", e);
        }
    }
}