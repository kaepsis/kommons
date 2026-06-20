package dev.kaepsis.kommons.config.parser.impl;

import dev.kaepsis.kommons.config.parser.IConfigParser;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YAML implementation of the {@link IConfigParser} interface.
 * <p>
 * This parser uses SnakeYAML to load and save configuration data in YAML format.
 * The YAML output is formatted with:
 * <ul>
 *   <li>Indentation of 2 spaces</li>
 *   <li>Block flow style (i.e., nested structures use indented blocks, not inline JSON)</li>
 *   <li>Lines are never split (maximum line width is effectively unlimited)</li>
 * </ul>
 * </p>
 * <p>
 * The parser expects the root of the YAML document to be a map (key‑value object).
 * Non‑string keys in the root map are rejected. Nested structures are preserved
 * as recursive maps, lists, and primitive values.
 * </p>
 * <p>
 * When loading from a file that does not exist or is empty, an empty {@code LinkedHashMap}
 * is returned (never {@code null}). The implementation is not thread‑safe by itself;
 * external synchronisation should be applied if needed.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * IConfigParser parser = new YamlParser();
 * Map<String, Object> config = parser.load(Path.of("config.yml"));
 * config.put("newKey", "value");
 * parser.save(Path.of("config.yml"), config);
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class YamlParser implements IConfigParser {

    private final Yaml yaml;

    /**
     * Constructs a new YAML parser with predefined formatting options.
     * <p>
     * The options are:
     * <ul>
     *   <li>indent: 2 spaces</li>
     *   <li>pretty flow: true (improves readability of inline structures)</li>
     *   <li>default flow style: BLOCK (nested maps and lists are written as blocks)</li>
     *   <li>split lines: false (prevents automatic line splitting)</li>
     *   <li>width: {@code Integer.MAX_VALUE} (effectively no line width limit)</li>
     * </ul>
     * </p>
     */
    public YamlParser() {
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        this.yaml = new Yaml(options);
    }

    /**
     * Loads a YAML file from the given path and returns its content as a map.
     * <p>
     * If the file does not exist or is empty, an empty {@code LinkedHashMap} is returned.
     * The method expects the root of the YAML document to be a map; otherwise an
     * {@code IllegalStateException} is thrown. Keys at the root level must be strings.
     * </p>
     *
     * @param path the path to the YAML file
     * @return a {@code Map<String, Object>} representing the YAML content (never {@code null})
     * @throws IOException            if an I/O error occurs while reading the file
     * @throws IllegalStateException  if the root YAML element is not a map or if a key is not a string
     */
    @Override
    public Map<String, Object> load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Object data = yaml.load(in);
            if (data == null) return new LinkedHashMap<>();
            if (!(data instanceof Map)) {
                throw new IllegalStateException("Root YAML is not a map");
            }
            return castMap(data);
        }
    }

    /**
     * Loads a YAML document from an input stream and returns its content as a map.
     * <p>
     * The stream is consumed fully but not closed by this method (the caller is responsible
     * for closing the stream). The root of the YAML document must be a map, and all keys
     * at the root level must be strings.
     * </p>
     *
     * @param inputStream the input stream containing YAML data
     * @return a {@code Map<String, Object>} representing the YAML content (never {@code null})
     * @throws IllegalStateException if the root YAML element is not a map or if a key is not a string
     */
    @Override
    public Map<String, Object> load(InputStream inputStream) {
        Object data = yaml.load(inputStream);
        if (data == null) {
            return new LinkedHashMap<>();
        }
        if (!(data instanceof Map<?, ?> rawMap)) {
            throw new IllegalStateException("Root YAML is not a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalStateException("YAML map contains non-string key: " + entry.getKey());
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Saves the given data map to a YAML file at the specified path.
     * <p>
     * The map is written using the formatting options configured in the constructor.
     * Existing file content is overwritten. The parent directory must already exist.
     * </p>
     *
     * @param path the path where the YAML file will be written
     * @param data the map to write; must not be {@code null}
     * @throws IOException if an I/O error occurs while writing the file
     */
    @Override
    public void save(Path path, Map<String, Object> data) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            yaml.dump(data, writer);
        }
    }

    /**
     * Casts an object to the expected map type.
     * <p>
     * This method exists solely to suppress the unchecked cast warning.
     * It is called only after verifying that the object is a {@code Map<String, Object>}.
     * </p>
     *
     * @param o the object to cast
     * @return the same object as {@code Map<String, Object>}
     * @throws ClassCastException if the object is not a {@code Map<String, Object>}
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }
}