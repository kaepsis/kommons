package dev.kaepsis.kommons.database.dao;

import dev.kaepsis.kommons.database.QueryManager;
import dev.kaepsis.kommons.database.annotations.Column;
import dev.kaepsis.kommons.database.annotations.Table;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An abstract base class for Data Access Objects (DAOs) that provide asynchronous
 * database operations for entities of type {@code T}.
 * <p>
 * This class uses reflection to map fields of the entity class to database columns,
 * based on the {@link Table} and {@link Column} annotations. It provides standard
 * CRUD operations ({@code find}, {@code insert}, {@code update}, {@code delete}) and
 * a table creation method. All methods return {@link CompletableFuture} for non‑blocking
 * asynchronous execution.
 * </p>
 * <p>
 * The entity class must be annotated with {@code @Table} to specify the table name,
 * and its persistent fields must be annotated with {@code @Column}. The DAO expects
 * a column named {@code uuid} to be present for {@link #findByUUID(String)} and
 * {@link #delete(String)}; this is a design assumption and can be overridden in
 * subclasses if needed.
 * </p>
 * <p>
 * Example usage for a concrete DAO:
 * <pre>{@code
 * @Table("players")
 * public class Player {
 *     @Column(value = "uuid", type = ColumnType.VARCHAR, length = 36, primaryKey = true)
 *     private String uuid;
 *
 *     @Column(value = "name", type = ColumnType.VARCHAR, length = 16)
 *     private String name;
 * }
 *
 * public class PlayerDao extends AbstractDao<Player> {
 *     public PlayerDao(QueryManager queryManager) {
 *         super(queryManager, Player.class);
 *     }
 * }
 *
 * PlayerDao dao = new PlayerDao(queryManager);
 * dao.createTable(Player.class);
 * dao.insert(player).thenAccept(rows -> ...);
 * }</pre>
 * </p>
 *
 * @param <T> the entity type (must be annotated with {@code @Table})
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public abstract class AbstractDao<T> {

    protected final QueryManager queryManager;
    private final Class<T> type;
    private final String tableName;

    /**
     * Constructs a new DAO for the given entity class, using the provided query manager.
     *
     * @param queryManager the query manager responsible for asynchronous SQL execution
     * @param type         the entity class (must be annotated with {@code @Table})
     */
    public AbstractDao(QueryManager queryManager, Class<T> type) {
        this.queryManager = queryManager;
        this.type = type;
        this.tableName = retrieveTableName(type);
    }

    /**
     * Asynchronously finds an entity by its UUID.
     * <p>
     * The query uses {@code WHERE uuid = ?}. The result is mapped to an instance of
     * {@code T} using {@link #mapRow(ResultSet)}.
     * </p>
     *
     * @param uuid the UUID of the entity (as a string)
     * @return a {@code CompletableFuture} that completes with the found entity,
     *         or {@code null} if no entity with that UUID exists
     */
    public CompletableFuture<T> findByUUID(String uuid) {
        String query = "SELECT * FROM " + tableName + " WHERE uuid = ?";
        return queryManager.queryForValue(query, this::mapRow, uuid);
    }

    /**
     * Asynchronously retrieves all entities from the table.
     *
     * @return a {@code CompletableFuture} that completes with a list of all entities
     *         (possibly empty)
     */
    public CompletableFuture<List<T>> findAll() {
        String query = "SELECT * FROM " + tableName;
        return queryManager.queryForList(query, this::mapRow);
    }

    /**
     * Asynchronously inserts the given entity into the database.
     * <p>
     * The insert query is built dynamically from the entity's fields using
     * {@link #buildInsertQuery()}, and field values are extracted with
     * {@link #extractValues(Object)}.
     * </p>
     *
     * @param entity the entity to insert
     * @return a {@code CompletableFuture} that completes with the number of affected rows
     */
    public CompletableFuture<Integer> insert(T entity) {
        String query = buildInsertQuery();
        Object[] params = extractValues(entity);
        return queryManager.executeUpdate(query, params);
    }

    /**
     * Asynchronously updates the given entity in the database.
     * <p>
     * The update query is built using {@link #buildUpdateQuery()}. All fields except
     * {@code uuid} are updated, and the {@code uuid} field is used in the {@code WHERE} clause.
     * </p>
     *
     * @param entity the entity to update (must contain a valid UUID field)
     * @return a {@code CompletableFuture} that completes with the number of affected rows
     */
    public CompletableFuture<Integer> update(T entity) {
        String query = buildUpdateQuery();
        Object[] params = extractValues(entity);
        return queryManager.executeUpdate(query, params);
    }

    /**
     * Asynchronously deletes an entity by its UUID.
     *
     * @param uuid the UUID of the entity to delete
     * @return a {@code CompletableFuture} that completes with the number of affected rows
     */
    public CompletableFuture<Integer> delete(String uuid) {
        String query = "DELETE FROM " + tableName + " WHERE uuid = ?";
        return queryManager.executeUpdate(query, uuid);
    }

    /**
     * Creates the database table for the given entity class if it does not already exist.
     * <p>
     * The SQL {@code CREATE TABLE IF NOT EXISTS} statement is generated based on the
     * {@code @Table} and {@code @Column} annotations. It respects column types, lengths,
     * decimal precision, nullability, primary keys, auto‑increment, and default values.
     * </p>
     * <p>
     * This method executes synchronously (blocking). For asynchronous table creation,
     * wrap the call in a {@code CompletableFuture.runAsync(...)}.
     * </p>
     *
     * @param type the entity class (must be annotated with {@code @Table})
     */
    public void createTable(Class<T> type) {
        String tableName = retrieveTableName(type);
        String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (";
        StringBuilder stringBuilder = new StringBuilder(query);
        List<String> columns = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column == null) continue;
            StringBuilder col = new StringBuilder();
            col.append(column.value()).append(" ");
            if (column.decimal()) {
                col.append(column.type().name())
                        .append("(").append(column.decimalValues()).append(") ");
            } else {
                col.append(column.type().name())
                        .append("(").append(column.length()).append(") ");
            }
            if (!column.defaultValue().isEmpty()) col.append("DEFAULT ").append(formatDefault(column)).append(" ");
            if (!column.nullable()) col.append("NOT NULL ");
            if (column.primaryKey()) col.append("PRIMARY KEY ");
            if (column.autoincrement()) col.append("AUTO_INCREMENT ");
            columns.add(col.toString().trim());
        }
        stringBuilder.append(String.join(", ", columns));
        stringBuilder.append(");");
        this.queryManager.execute(stringBuilder.toString());
    }

    private String formatDefault(Column column) {
        String value = column.defaultValue();
        if (value.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return value;
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return value;
        }
        return "'" + value + "'";
    }

    private String retrieveTableName(Class<T> type) {
        return type.getAnnotation(Table.class).value();
    }

    private T mapRow(ResultSet rs) throws SQLException {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = rs.getObject(field.getName());
                field.set(instance, value);
            }
            return instance;
        } catch (Exception e) {
            throw new SQLException("Error while mapping object. ", e);
        }
    }

    private String buildInsertQuery() {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (Field field : type.getDeclaredFields()) {
            if (!columns.isEmpty()) columns.append(", ");
            columns.append(field.getName());
            if (!values.isEmpty()) values.append(", ");
            values.append("?");
        }
        return "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
    }

    private String buildUpdateQuery() {
        StringBuilder set = new StringBuilder();
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals("uuid")) continue;
            if (!set.isEmpty()) set.append(", ");
            set.append(field.getName()).append(" = ?");
        }
        return "UPDATE " + tableName + " SET " + set + " WHERE uuid = ?";
    }

    private Object[] extractValues(T entity) {
        List<Object> values = new ArrayList<>();
        try {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                values.add(field.get(entity));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return values.toArray();
    }

}