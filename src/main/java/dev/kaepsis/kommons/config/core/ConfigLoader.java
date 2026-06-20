package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.annotations.ConfigFile;
import dev.kaepsis.kommons.config.annotations.ConfigValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads, saves, and manages an integrated configuration mapping pipeline for a target class
 * annotated with {@link ConfigFile}.
 * <p>
 * This class extends {@link AbstractConfigLoader} to provide full type conversion features.
 * It reflectively unpacks structural file properties into standard Java types, covering:
 * <ul>
 * <li>Primitives and their respective object wrappers (e.g., {@code int}, {@code boolean}, {@code double}).</li>
 * <li>Standard collections and arbitrary mappings ({@link Map}).</li>
 * <li>Highly nested configurations, binding sub-maps recursively into dedicated custom object schemas.</li>
 * </ul>
 * </p>
 * <p>
 * State mutations applied via {@link #setValue(String, Object)} trigger an absolute structural
 * refresh on the mapping instances, before systematically flushing changes back onto disk storage.
 * </p>
 *
 * @param <T> the type of the domain configuration container class managed by this loader
 * @see AbstractConfigLoader
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class ConfigLoader<T> extends AbstractConfigLoader<T> {

    /** The internal structural version mapping identifier property key used inside configuration files. */
    private static final String VERSION_KEY = "config-version";

    /**
     * Resolves the primary configuration filename driven by class metadata elements before
     * the core super-constructor initializes the backing file format parser pipeline.
     */
    private static String resolveFileName(Class<?> configClass) {
        ConfigFile meta = configClass.getAnnotation(ConfigFile.class);
        if (meta == null) throw new IllegalStateException("Missing @ConfigFile on " + configClass.getSimpleName());
        return meta.name();
    }

    private final ConfigContainer container = new ConfigContainer();
    private final Path path;
    private final int expectedVersion;

    /**
     * Constructs a new {@code ConfigLoader} instance linked to a specific configuration target context.
     * <p>
     * Scans and verifies the required {@link ConfigFile} annotation properties on the class definition
     * metadata tree, maps target filesystem properties, and exports pristine asset files directly
     * out of embedded JAR resource streams if missing on disk.
     * </p>
     *
     * @param configClass the metadata configuration {@link Class} container blueprint type
     * @param plugin      the hosting {@link JavaPlugin} platform runtime execution context instance
     * @throws IllegalStateException if the target configuration container class is missing the {@link ConfigFile} annotation
     */
    public ConfigLoader(Class<T> configClass, JavaPlugin plugin) {
        super(configClass, plugin, ConfigParsers.forFile(resolveFileName(configClass)));

        ConfigFile meta = configClass.getAnnotation(ConfigFile.class);
        if (meta == null) throw new IllegalStateException("Missing @ConfigFile on " + configClass.getSimpleName());

        this.expectedVersion = meta.version();
        ensureDataFolder();
        this.path = plugin.getDataFolder().toPath().resolve(meta.name());

        if (!Files.exists(path)) plugin.saveResource(meta.name(), false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Synchronously resolves file hierarchies across defaults and disk spaces, processes schema
     * version merges, initializes memory storage containers, and updates internal instance reference variables.
     * </p>
     *
     * @throws RuntimeException if reflecting properties or handling mapping parameter streams encounters processing faults
     */
    @Override
    public void load() {
        Map<String, Object> defaults = loadFromResources(configClass.getAnnotation(ConfigFile.class).name());
        Map<String, Object> user = loadFromDisk(path);
        Map<String, Object> merged = merge(defaults, user);

        int storedVersion = getVersion(user, VERSION_KEY);
        merged.put(VERSION_KEY, Math.max(storedVersion, expectedVersion));

        container.replace(merged);
        try {
            instance = bindValues();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Re-runs the configuration mapping layer workflow to refresh state variables from modified files.
     * </p>
     */
    @Override
    public void reload() {
        load();
    }

    /**
     * Flushes the current point-in-time snapshot state of the internal configuration
     * memory container directly down onto the designated filesystem path.
     */
    public void save() {
        saveToDisk(path, container.snapshot());
    }

    /**
     * Updates a specific configuration property inside the storage container, dynamically re-binds
     * the configuration instance fields to mirror the updated state, and pushes updates directly onto disk.
     *
     * @param key   the exact hierarchical configuration node key to target
     * @param value the raw object value to associate with the designated configuration node key
     * @throws RuntimeException if mapping parameter values or running post-save reflective binds fail
     */
    public void setValue(String key, Object value) {
        container.set(key, value);
        try {
            instance = bindValues();
        } catch (Exception e) {
            throw new RuntimeException("Failed to bind after setValue: " + key, e);
        }
        save();
    }

    // -------------------------------------------------------------------------
    // Binding & conversion
    // -------------------------------------------------------------------------

    /**
     * Instantiates a pristine target configuration class type container and reflectively populates
     * fields carrying the explicit {@link ConfigValue} structural markers.
     *
     * @return a fully populated instance of the configuration class type
     * @throws Exception if constructors are inaccessible, or field mapping logic encounters type mismatches
     */
    private T bindValues() throws Exception {
        T obj = configClass.getDeclaredConstructor().newInstance();
        for (Field field : configClass.getDeclaredFields()) {
            ConfigValue cv = field.getAnnotation(ConfigValue.class);
            if (cv == null) continue;
            Object value = container.get(cv.value());
            field.setAccessible(true);
            field.set(obj, convertField(value, field));
        }
        return obj;
    }

    /**
     * Assesses field signature declarations to process complex parameters, isolating generic maps,
     * parameter types, and deep nesting structures before delegating down onto base converters.
     * <p>
     * If a field is identified as a parameterized map holding complex custom types as values, this method
     * unpacks incoming sub-elements and transforms them recursively into target object blueprints.
     * </p>
     *
     * @param value the raw object node value fetched out of the structural map tree
     * @param field the explicit target class {@link Field} reflection definition context
     * @return a converted, fully-formed object structure matched to the field type signature
     * @throws Exception if underlying reflection routines or conversion rules encounter compliance errors
     */
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
                        if (entry.getValue() instanceof Map<?, ?> nested) {
                            Map<String, Object> nestedMap = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> ne : nested.entrySet())
                                nestedMap.put(ne.getKey().toString(), ne.getValue());
                            result.put(entry.getKey().toString(), bindObjectFromMap(nestedMap, valueClass));
                        }
                    }
                    return result;
                }
            }
        }
        return convert(value, field.getType());
    }

    /**
     * Converts a raw input value into a definitive target class structure type, applying primitive parsing
     * boundaries, string translation layers, or triggering object mapping routines when required.
     *
     * @param value the raw structural value data source to parse
     * @param type  the objective target {@link Class} destination format requirements
     * @return a concrete value instance matching the target class requirements
     * @throws IllegalArgumentException if no safe conversion route can be derived between incoming data types and target assignments
     * @throws Exception                 if underlying numeric string parsing errors occur
     */
    private Object convert(Object value, Class<?> type) throws Exception {
        if (value == null) return null;
        if (type.isPrimitive()) {
            if (type == int.class)     return value instanceof Number n ? n.intValue()    : Integer.parseInt(value.toString());
            if (type == boolean.class) return value instanceof Boolean  ? value           : Boolean.parseBoolean(value.toString());
            if (type == double.class)  return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
            if (type == float.class)   return value instanceof Number n ? n.floatValue()  : Float.parseFloat(value.toString());
            if (type == long.class)    return value instanceof Number n ? n.longValue()   : Long.parseLong(value.toString());
            if (type == short.class)   return value instanceof Number n ? n.shortValue()  : Short.parseShort(value.toString());
            if (type == byte.class)    return value instanceof Number n ? n.byteValue()   : Byte.parseByte(value.toString());
            return null;
        }
        if (type.isInstance(value)) return value;
        if (type == String.class) return value.toString();
        if (type == Map.class && value instanceof Map<?, ?>) return value;
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> safeMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String))
                    throw new IllegalArgumentException("Non-string key: " + entry.getKey());
                safeMap.put(entry.getKey().toString(), entry.getValue());
            }
            return bindObjectFromMap(safeMap, type);
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to " + type);
    }

    /**
     * Reflectively constructs an instance of a specified helper class type and iteratively populates
     * its declared fields matching properties present inside a raw string key-value attributes map.
     *
     * @param <R>  the generic object instance type being assembled
     * @param map  the raw safe attributes key-value node configuration data source
     * @param type the target blueprint destination {@link Class} to map down into
     * @return a completely bound helper object structure
     * @throws Exception if constructors are inaccessible, or internal property conversions fail
     */
    private <R> R bindObjectFromMap(Map<String, Object> map, Class<R> type) throws Exception {
        R obj = type.getDeclaredConstructor().newInstance();
        for (Field field : type.getDeclaredFields()) {
            field.setAccessible(true);
            Object raw = map.get(field.getName());
            if (raw != null) field.set(obj, convert(raw, field.getType()));
        }
        return obj;
    }
}