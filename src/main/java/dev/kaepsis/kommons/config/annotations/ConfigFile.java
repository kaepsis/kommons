package dev.kaepsis.kommons.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration file container.
 * <p>
 * Classes annotated with {@code @ConfigFile} are expected to hold configuration values
 * (typically static fields) that should be loaded from, or saved to, a YAML or properties file.
 * The annotation provides the file name and a version number for compatibility and auto‑update logic.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @ConfigFile(name = "settings.yml", version = 260514)
 * public class Settings {
 *     @ConfigValue("database.host")
 *     public static String DB_HOST = "localhost";
 *
 *     @ConfigValue("database.port")
 *     public static int DB_PORT = 3306;
 * }
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile {

    /**
     * The name of the configuration file (e.g., "config.yml", "settings.conf").
     * <p>
     * This name is used to locate the file within the plugin's data folder or a predefined
     * configuration directory. It may include subdirectories separated by slashes.
     * </p>
     *
     * @return the configuration file name (never empty)
     */
    String name();

    /**
     * The version of the configuration file structure.
     * <p>
     * Can be used to automatically migrate old configuration files to a newer format
     * when the plugin updates. A mismatch between the expected version and the file's
     * stored version may trigger a reload or backup.
     * </p>
     *
     * @return the version number, defaults to 1
     */
    int version() default 1;

}