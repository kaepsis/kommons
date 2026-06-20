package dev.kaepsis.kommons.config.parser.impl;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import dev.kaepsis.kommons.config.parser.IConfigParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A TOML (Tom's Obvious, Minimal Language) implementation of the {@link IConfigParser} interface
 * powered by the {@code night-config} library.
 * <p>
 * This class provides facilities to serialize and deserialize hierarchical configurations.
 * To remain compatible and uniform with other parsers (such as a {@code YamlParser}),
 * recursive internal mappings are performed:
 * <ul>
 * <li>Nested TOML sections/tables are flattened or translated down into standard nested {@code Map<String, Object>} instances.</li>
 * <li>Order is preserved across operations through the extensive use of {@link LinkedHashMap}.</li>
 * </ul>
 * </p>
 * <p>
 * All reading and writing operations explicitly enforce the {@code UTF-8} character encoding specification standard.
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class TomlParser implements IConfigParser {

    /**
     * {@inheritDoc}
     * <p>
     * Reads a TOML configuration file from the specified {@link Path} using a buffered reader
     * and converts it into a nested {@link Map}.
     * </p>
     *
     * @param path the filesystem {@link Path} pointing to the target TOML file
     * @return a {@link Map} representing the hierarchical configuration tree
     * @throws IOException if an I/O error occurs while opening or reading the file,
     * or if the underlying file structure contains invalid TOML syntax
     */
    @Override
    public Map<String, Object> load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return toMap(TomlFormat.instance().createParser().parse(reader));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Parses a raw raw data byte stream representing a TOML document into a nested {@link Map}.
     * </p>
     * <p>
     * <b>Note:</b> Responsibility for closing the provided {@link InputStream} remains with the caller.
     * </p>
     *
     * @param inputStream the {@link InputStream} source containing TOML raw text data bytes
     * @return a {@link Map} representing the hierarchical configuration tree
     * @throws com.electronwill.nightconfig.core.io.ParsingException if the stream content contains malformed TOML syntax
     */
    @Override
    public Map<String, Object> load(InputStream inputStream) {
        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        return toMap(TomlFormat.instance().createParser().parse(reader));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Transforms the given hierarchical {@link Map} into an internal {@link Config} instance,
     * and serializes it in standard TOML format to the target file destination path.
     * </p>
     *
     * @param path the filesystem {@link Path} where the generated configuration output should be stored
     * @param data the structured key-value data hierarchy map to serialize
     * @throws IOException if an I/O exception occurs while initializing the file writer or dumping data
     */
    @Override
    public void save(Path path, Map<String, Object> data) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            TomlFormat.instance().createWriter().write(toConfig(data), writer);
        }
    }

    // -------------------------------------------------------------------------
    // Conversion — Config <-> Map
    // -------------------------------------------------------------------------

    /**
     * Recursively transforms a {@code night-config} {@link Config} object into a standard,
     * decoupled nested Java {@link Map}.
     * <p>
     * Iterates over every configuration entry. If an entry value is found to be a nested
     * {@code Config} sub-section, it is recursively mapped downwards to a new {@code LinkedHashMap}.
     * </p>
     *
     * @param config the source {@link Config} object tree to convert
     * @return a corresponding {@link Map} implementation holding standard Java types
     */
    private Map<String, Object> toMap(Config config) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Config.Entry entry : config.entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value instanceof Config nested ? toMap(nested) : value);
        }
        return result;
    }

    /**
     * Recursively transforms a standard hierarchical Java {@link Map} into a compatible
     * {@code night-config} {@link Config} mapping instance suitable for TOML serialization.
     * <p>
     * Each nested {@code Map} found within the value tree branches is dynamically bound
     * and converted to a sub-{@code Config} section using a {@link LinkedHashMap} backing provider structure
     * to ensure key placement ordering stability.
     * </p>
     *
     * @param map the raw source metadata map configuration tree to parse
     * @return a structured {@link Config} element representing the processed state
     */
    @SuppressWarnings("unchecked")
    private Config toConfig(Map<String, Object> map) {
        Config config = TomlFormat.instance().createConfig(LinkedHashMap::new);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            config.set(entry.getKey(), value instanceof Map<?, ?> nested
                    ? toConfig((Map<String, Object>) nested)
                    : value);
        }
        return config;
    }
}