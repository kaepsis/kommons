package dev.kaepsis.kommons.database.annotations;

import dev.kaepsis.kommons.database.enums.ColumnType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a column mapping for a database table field.
 * <p>
 * This annotation is used on fields within a class annotated with {@link Table}.
 * It specifies how the Java field should be mapped to a database column,
 * including column name, SQL data type, length, nullability, default value,
 * and primary key / auto‑increment properties.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * @Table("users")
 * public class User {
 *     @Column(value = "id", type = ColumnType.INT, primaryKey = true, autoincrement = true)
 *     private int id;
 *
 *     @Column(value = "username", type = ColumnType.VARCHAR, length = 50, nullable = false)
 *     private String username;
 *
 *     @Column(value = "balance", type = ColumnType.DECIMAL, decimal = true, decimalValues = "15,2")
 *     private BigDecimal balance;
 * }
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /**
     * The name of the database column.
     * <p>
     * If not specified (empty string), the field name will be used as the column name.
     * </p>
     *
     * @return the column name, or an empty string to use the field name
     */
    String value() default "";

    /**
     * The SQL column type (e.g., {@code VARCHAR}, {@code INT}, {@code TIMESTAMP}).
     * <p>
     * This must be one of the values defined in {@link ColumnType}. The type
     * determines how the Java value is stored and retrieved.
     * </p>
     *
     * @return the column type (never {@code null})
     */
    ColumnType type();

    /**
     * The column length (for string types like {@code VARCHAR}) or precision.
     * <p>
     * Only applicable for types that support a length attribute (e.g., {@code VARCHAR},
     * {@code CHAR}). Ignored for other types.
     * </p>
     *
     * @return the column length, defaults to 255
     */
    int length() default 255;

    /**
     * Whether the column can contain {@code NULL} values.
     *
     * @return {@code true} if nullable (default), {@code false} for {@code NOT NULL}
     */
    boolean nullable() default true;

    /**
     * Whether the column is a decimal type with configurable precision and scale.
     * <p>
     * When {@code true}, the {@link #decimalValues()} attribute is used to specify
     * the precision and scale (e.g., {@code "10,2"}). This is typically used with
     * {@code ColumnType.DECIMAL}.
     * </p>
     *
     * @return {@code true} if this column represents a decimal with custom precision
     */
    boolean decimal() default false;

    /**
     * The precision and scale for a decimal column, in the format {@code "precision,scale"}.
     * <p>
     * Example: {@code "10,2"} means a total of 10 digits with 2 digits after the decimal point.
     * Only used when {@link #decimal()} is {@code true}.
     * </p>
     *
     * @return the decimal format string, defaults to {@code "10,2"}
     */
    String decimalValues() default "10,2";

    /**
     * Whether this column is part of the primary key.
     * <p>
     * If multiple columns have {@code primaryKey = true}, a composite primary key is created.
     * </p>
     *
     * @return {@code true} if this column is (part of) the primary key
     */
    boolean primaryKey() default false;

    /**
     * Whether the column should be auto‑incremented (typically for integer primary keys).
     * <p>
     * Requires the underlying database to support auto‑increment (e.g., MySQL {@code AUTO_INCREMENT},
     * SQLite {@code AUTOINCREMENT}). Auto‑increment columns are usually also primary keys.
     * </p>
     *
     * @return {@code true} if the column value is generated automatically by the database
     */
    boolean autoincrement() default false;

    /**
     * The default value for the column when no value is provided during insert.
     * <p>
     * The value must be a string representation compatible with the SQL column type.
     * This attribute is omitted if left empty (the default).
     * </p>
     *
     * @return the default value as a string, or an empty string for no default
     */
    String defaultValue() default "";

}