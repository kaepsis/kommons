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

public class I18nLoader<T> {

    private final Class<T> configClass;
    private final JavaPlugin plugin;
    private final IConfigParser parser;
    private final String baseName;
    private final String defaultLocale;
    private final int expectedVersion;

    private String activeLocale;
    private volatile T instance;

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

    public T getInstance() {
        return instance;
    }

    public String getActiveLocale() {
        return activeLocale;
    }

    public void load() {
        String fileName = resolveFileName(activeLocale);
        Map<String, Object> data = loadFile(fileName);
        instance = bindValues(data);
    }

    public void reload() {
        load();
    }

    public void switchLocale(String newLocale) {
        this.activeLocale = newLocale;
        load();
    }

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