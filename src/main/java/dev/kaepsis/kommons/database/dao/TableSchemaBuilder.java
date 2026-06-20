package dev.kaepsis.kommons.database.dao;

import dev.kaepsis.kommons.database.annotations.Column;
import dev.kaepsis.kommons.database.annotations.Table;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * A package-private utility class responsible for dynamically generating SQL
 * {@code CREATE TABLE IF NOT EXISTS} statements from annotated Java classes.
 * <p>
 * This builder scans the target class metadata via reflection, mapping the class to a
 * database table using {@link Table} and its declared fields to SQL columns using {@link Column}.
 * </p>
 * <p>
 * <b>Note:</b> This class is utility-only, non-instantiable, and operates sequentially
 * over the target class's declared fields.
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
final class TableSchemaBuilder {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TableSchemaBuilder() {}

    /**
     * Generates a complete {@code CREATE TABLE IF NOT EXISTS} SQL statement for the specified class type.
     * <p>
     * The method extracts the table name from the {@link Table} annotation and iterates over all
     * declared fields of the class, appending generated column definitions for any field
     * marked with the {@link Column} annotation. Non-annotated fields are ignored.
     * </p>
     *
     * @param <T>  the generic type of the class being processed
     * @param type the {@link Class} object of the entity to map to a database table schema
     * @return a valid SQL {@code CREATE TABLE IF NOT EXISTS} string
     * @throws IllegalArgumentException if the provided class is missing the {@link Table} annotation
     */
    static <T> String build(Class<T> type) {
        Table table = type.getAnnotation(Table.class);
        if (table == null)
            throw new IllegalArgumentException(type.getSimpleName() + " is missing @Table");

        List<String> cols = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column col = field.getAnnotation(Column.class);
            if (col == null) continue;
            cols.add(buildColumnDef(col));
        }

        return "CREATE TABLE IF NOT EXISTS " + table.value()
                + " (" + String.join(", ", cols) + ");";
    }

    /**
     * Constructs the SQL column definition fragment for a single {@link Column} annotation.
     * <p>
     * This method handles the parsing of:
     * <ul>
     * <li>Column name and data type.</li>
     * <li>Type sizing parameters (e.g., fractional precision for decimals vs. length boundaries for strings).</li>
     * <li>Column constraints like {@code DEFAULT}, {@code NOT NULL}, {@code PRIMARY KEY}, and {@code AUTO_INCREMENT}.</li>
     * </ul>
     * </p>
     *
     * @param col the {@link Column} annotation instance containing the structural metadata
     * @return a string representing the column's SQL definition fragment (e.g., {@code "id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT"})
     */
    private static String buildColumnDef(Column col) {
        StringBuilder def = new StringBuilder(col.value()).append(" ");

        if (col.decimal()) {
            def.append(col.type().name()).append("(").append(col.decimalValues()).append(") ");
        } else {
            def.append(col.type().name()).append("(").append(col.length()).append(") ");
        }

        if (!col.defaultValue().isEmpty()) def.append("DEFAULT ").append(formatDefault(col)).append(" ");
        if (!col.nullable())     def.append("NOT NULL ");
        if (col.primaryKey())    def.append("PRIMARY KEY ");
        if (col.autoincrement()) def.append("AUTO_INCREMENT ");

        return def.toString().trim();
    }

    /**
     * Formats the raw default value specified in the {@link Column} annotation to match SQL syntax standard requirements.
     * <p>
     * Formatting rules applied:
     * <ul>
     * <li>SQL keywords (such as {@code CURRENT_TIMESTAMP}) are retained unquoted.</li>
     * <li>Numeric values (integers and floating-point decimals) are retained unquoted.</li>
     * <li>Textual and arbitrary string values are automatically wrapped inside single quotes ({@code 'value'}).</li>
     * </ul>
     * </p>
     *
     * @param col the {@link Column} annotation containing the raw default value string
     * @return a properly escaped/formatted SQL literal or keyword string representing the default value
     */
    private static String formatDefault(Column col) {
        String value = col.defaultValue();
        if (value.equalsIgnoreCase("CURRENT_TIMESTAMP")) return value;
        if (value.matches("-?\\d+(\\.\\d+)?")) return value;
        return "'" + value + "'";
    }
}