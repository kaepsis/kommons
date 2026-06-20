package dev.kaepsis.kommons.config.core;

import dev.kaepsis.kommons.config.parser.IConfigParser;
import dev.kaepsis.kommons.config.parser.impl.TomlParser;
import dev.kaepsis.kommons.config.parser.impl.YamlParser;

/**
 * A package-private factory utility responsible for mapping file names to their
 * corresponding configuration parser implementation based on file extensions.
 * <p>
 * This class serves as a centralized registration and resolution hub for configuration formats.
 * If a new format (such as JSON or HOCON) needs to be supported by the framework, its parser
 * mapping logic should be integrated directly into this class.
 * </p>
 * <p>
 * <b>Note:</b> This class is utility-only, non-instantiable, and package-private to prevent
 * exposing internal factory mechanics outside of the core configuration subsystem.
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
final class ConfigParsers {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ConfigParsers() {}

    /**
     * Resolves and returns the appropriate {@link IConfigParser} instance for the specified file name.
     * <p>
     * Resolution rules applied:
     * <ul>
     * <li>If the file name ends with {@code .toml}, a {@link TomlParser} instance is returned.</li>
     * <li>For all other extensions (acting as a fallback default, primarily covering {@code .yml}
     * and {@code .yaml}), a {@link YamlParser} instance is returned.</li>
     * </ul>
     * </p>
     *
     * @param fileName the name or path string of the configuration file to evaluate
     * @return a dedicated {@link IConfigParser} capable of handling the detected file format
     * @throws NullPointerException if the provided {@code fileName} is null
     */
    static IConfigParser forFile(String fileName) {
        if (fileName.endsWith(".toml")) return new TomlParser();
        return new YamlParser(); // default — covers .yml and .yaml
    }
}