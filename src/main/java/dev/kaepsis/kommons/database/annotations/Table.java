package dev.kaepsis.kommons.database.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a database table mapping.
 * <p>
 * Classes annotated with {@code @Table} represent a table in the database.
 * The annotation specifies the actual table name. Inside such a class, fields
 * should be annotated with {@link Column} to define column mappings.
 * </p>
 * <p>
 * The table structure can be automatically generated or validated by the database
 * layer using these annotations.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @Table("players")
 * public class PlayerData {
 *     @Column(value = "uuid", type = ColumnType.VARCHAR, length = 36, primaryKey = true)
 *     private String uuid;
 *
 *     @Column(value = "name", type = ColumnType.VARCHAR, length = 16, nullable = false)
 *     private String name;
 *
 *     @Column(value = "level", type = ColumnType.INT, nullable = false)
 *     private int level;
 * }
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /**
     * The name of the database table.
     * <p>
     * This value is required and must be a valid table identifier (e.g., {@code "users"},
     * {@code "player_data"}). The table name may be quoted or escaped depending on the
     * database dialect.
     * </p>
     *
     * @return the table name (never empty)
     */
    String value();

}