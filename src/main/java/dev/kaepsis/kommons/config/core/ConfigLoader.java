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

public class ConfigLoader<T> {

    private final Class<T> configClass;
    private final ConfigContainer container = new ConfigContainer();
    private final JavaPlugin plugin;
    private Path path;
    private IConfigParser parser;
    private volatile T instance;
    private int expectedVersion;

    public ConfigLoader(Class<T> configClass, JavaPlugin plugin) {
        this.configClass = configClass;
        this.plugin = plugin;
        init();
    }

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

    public void save() {
        try {
            parser.save(path, container.snapshot());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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