package dev.kaepsis.kommons.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a field to a specific key in a configuration file.
 * <p>
 * When used inside a class annotated with {@link ConfigFile}, this annotation indicates that
 * the annotated field's value should be read from (and optionally written to) the configuration
 * file under the given key path. Nested keys are typically separated by dots.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @ConfigFile(name = "database.yml", version = 260514)
 * public class DatabaseConfig {
 *     @ConfigValue("connection.url")
 *     public static String URL = "jdbc:mysql://localhost:3306/db";
 *
 *     @ConfigValue("connection.pool.size")
 *     public static int POOL_SIZE = 10;
 * }
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigValue {

    /**
     * The configuration key path that this field maps to.
     * <p>
     * The path may use dot notation to represent nested sections (e.g., {@code "database.host"}).
     * The underlying configuration loader will interpret this key to retrieve or store the value.
     * </p>
     *
     * @return the configuration key (never empty)
     */
    String value();

}