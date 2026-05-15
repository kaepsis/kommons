package dev.kaepsis.kommons.config.annotations.i18n;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a language file container for internationalization (i18n) purposes.
 * <p>
 * Classes annotated with {@code @LangFile} are expected to hold localized message keys and
 * values, typically used together with a resource bundle mechanism. The annotation provides
 * metadata about the language file's base name, default locale, and version for compatibility
 * and reloading logic.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @LangFile(baseName = "messages", defaultLocale = "en", version = 260514)
 * public class Messages {
 *     public static final String GREETING = "greeting";
 *     public static final String FAREWELL = "farewell";
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
public @interface LangFile {

    /**
     * The base name of the language file (e.g., "messages", "gui_titles").
     * <p>
     * This name is used to locate the corresponding resource bundle files, typically
     * following the pattern {@code baseName_locale.properties}.
     * </p>
     *
     * @return the base name of the language file (never empty)
     */
    String baseName();

    /**
     * The default locale to use when a specific locale is not available or requested.
     * <p>
     * Locales should be specified as lowercase language codes (e.g., "en", "de", "es").
     * This value is used as a fallback when a translation for the target locale is missing.
     * </p>
     *
     * @return the default locale code, defaults to "en"
     */
    String defaultLocale() default "en";

    /**
     * The version of the language file structure or content.
     * <p>
     * This can be used for cache invalidation, hot reloading checks, or ensuring
     * that the language file is compatible with the plugin's expected version.
     * </p>
     *
     * @return the version number, defaults to 1
     */
    int version() default 1;

}