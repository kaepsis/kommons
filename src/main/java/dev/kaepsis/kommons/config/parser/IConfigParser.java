package dev.kaepsis.kommons.config.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * Defines the contract for configuration file parsers (e.g., YAML, JSON, properties).
 * <p>
 * Implementations of this interface are responsible for reading configuration data
 * from various sources (files or input streams) into a {@code Map<String, Object>}
 * structure, and for writing such a map back to a file. The map structure typically
 * mirrors the hierarchical nature of configuration files: nested maps represent
 * sections or sub‑objects, while leaf values are primitive types, strings, or lists.
 * </p>
 * <p>
 * Parsers are expected to be thread‑safe only if explicitly documented by the
 * implementation. Callers should assume that instances are not shared across threads
 * unless stated otherwise.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * IConfigParser parser = new YamlParser();
 * Map<String, Object> data = parser.load(Path.of("config.yml"));
 * data.put("newSetting", 42);
 * parser.save(Path.of("config.yml"), data);
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public interface IConfigParser {

    /**
     * Loads configuration data from a file at the given path.
     * <p>
     * The returned map preserves the order of keys as they appear in the source
     * file if the implementation supports it (e.g., YAML parsers using {@code LinkedHashMap}).
     * </p>
     *
     * @param path the path to the configuration file
     * @return a map containing the parsed configuration (never {@code null};
     *         an empty map if the file is empty or contains no data)
     * @throws Exception if the file cannot be read, is malformed, or any other
     *                   parsing error occurs (the exact exception type depends on the implementation)
     */
    Map<String, Object> load(Path path) throws Exception;

    /**
     * Loads configuration data from an input stream.
     * <p>
     * The stream is consumed but <strong>not</strong> closed by this method;
     * the caller is responsible for closing the stream after the method returns.
     * </p>
     *
     * @param inputStream the input stream containing the configuration data
     * @return a map containing the parsed configuration (never {@code null};
     *         an empty map if the stream contains no data)
     * @throws IOException if an I/O error occurs while reading the stream
     */
    Map<String, Object> load(InputStream inputStream) throws IOException;

    /**
     * Writes configuration data to a file at the given path.
     * <p>
     * The output format (indentation, line width, flow style) depends on the
     * specific implementation. Existing file content is overwritten. Parent
     * directories are expected to exist; they are not created automatically.
     * </p>
     *
     * @param path the path where the configuration will be written
     * @param data the configuration data to write (must not be {@code null})
     * @throws Exception if the file cannot be written, the data cannot be
     *                   serialised, or any other error occurs
     */
    void save(Path path, Map<String, Object> data) throws Exception;

}