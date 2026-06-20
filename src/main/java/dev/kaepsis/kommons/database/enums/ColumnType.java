package dev.kaepsis.kommons.database.enums;

/**
 * Enumeration of supported SQL column types for database mapping.
 * <p>
 * These types are used in the {@link dev.kaepsis.kommons.database.annotations.Column}
 * annotation to define the database column type for a given field. The actual SQL
 * dialect (MySQL, SQLite, PostgreSQL) is handled by the underlying database layer;
 * this enum provides a database‑independent abstraction.
 * </p>
 * <p>
 * The supported types are:
 * <ul>
 *   <li>{@code INT} – integer (32‑bit)</li>
 *   <li>{@code BIGINT} – large integer (64‑bit)</li>
 *   <li>{@code DECIMAL} – exact numeric with precision and scale</li>
 *   <li>{@code TEXT} – long character data</li>
 *   <li>{@code CHAR} – fixed‑length character string</li>
 *   <li>{@code VARCHAR} – variable‑length character string</li>
 *   <li>{@code DATE} – date (year, month, day)</li>
 *   <li>{@code TIME} – time (hours, minutes, seconds)</li>
 *   <li>{@code TIMESTAMP} – date and time</li>
 * </ul>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ColumnType {
    INT,
    BIGINT,
    DECIMAL,
    TEXT,
    CHAR,
    VARCHAR,
    DATE,
    TIME,
    TIMESTAMP
}