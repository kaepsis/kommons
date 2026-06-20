package dev.kaepsis.kommons.database.dao;

import dev.kaepsis.kommons.database.QueryManager;
import dev.kaepsis.kommons.database.annotations.Column;
import dev.kaepsis.kommons.database.annotations.Table;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * An abstract Data Access Object (DAO) providing asynchronous CRUD (Create, Read, Update, Delete)
 * operations for entity classes mapped via {@link Table} and {@link Column} annotations.
 * <p>
 * To minimize the performance overhead typically associated with reflection, this class parses,
 * computes, and caches all SQL queries ({@code INSERT}, {@code UPDATE}, {@code UPSERT}) and mapped
 * {@link Field} references once during object construction. Subsequent database operations use these
 * cached strings and fields directly.
 * </p>
 * <p>
 * All database operations interact through a {@link QueryManager} and return {@link CompletableFuture}
 * handles, making them entirely non-blocking and safe to use in performance-critical execution paths
 * (such as application or game server main loops).
 * </p>
 *
 * @param <T> the type of the domain entity managed by this DAO
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class AbstractDao<T> {

    /** The query executor responsible for managing database connections and thread pools. */
    protected final QueryManager queryManager;

    private final Class<T> type;
    private final String tableName;
    private final String primaryKeyColumn;

    // cached at construction — reflection runs once, not on every call
    private final String insertQuery;
    private final String updateQuery;
    private final String upsertQuery;
    private final List<Field> cachedColumnFields;
    private final List<Field> cachedAllColumnFields;

    /**
     * Constructs a new {@code AbstractDao} instance, resolving and caching the metadata of the target class.
     * <p>
     * During initialization, this constructor scans the provided class type via reflection to identify
     * its database table name, primary key configuration, and structural column fields. Using this data,
     * it pre-compiles the template string statements for standard SQL database operations.
     * </p>
     *
     * @param queryManager the {@link QueryManager} instance used to delegate database calls
     * @param type         the {@link Class} type of the entity managed by this DAO
     * @throws IllegalArgumentException if the provided class is missing the {@link Table} annotation
     * @throws IllegalStateException    if the provided class does not declare a primary key column
     */
    public AbstractDao(QueryManager queryManager, Class<T> type) {
        this.queryManager = queryManager;
        this.type = type;
        this.tableName = resolveTableName(type);
        this.primaryKeyColumn = resolvePrimaryKeyColumn();
        this.cachedAllColumnFields = buildColumnFields(false);
        this.cachedColumnFields = buildColumnFields(true);
        this.insertQuery = buildInsertQuery();
        this.updateQuery = buildUpdateQuery();
        this.upsertQuery = buildUpsertQuery();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Asynchronously retrieves an entity from the database by its primary key identifier.
     *
     * @param id the primary key identifier value (e.g., a UUID, Long, or Integer)
     * @return a {@link CompletableFuture} supplying an {@link Optional} containing the mapped
     * entity instance if found; otherwise an empty {@link Optional}
     */
    public CompletableFuture<Optional<T>> findById(Object id) {
        String query = "SELECT * FROM " + tableName + " WHERE " + primaryKeyColumn + " = ?";
        return queryManager.queryForValue(query, this::mapRow, id)
                .thenApply(Optional::ofNullable);
    }

    /**
     * Asynchronously retrieves a list of all entities in the database up to a default safety ceiling.
     * <p>
     * To prevent accidental memory exhaustion or network throttling over large datasets, this call
     * defaults to an internal constraint of {@code LIMIT 1000 OFFSET 0}.
     * </p>
     *
     * @return a {@link CompletableFuture} supplying a {@link List} of all mapped entity instances found
     * @see #findAll(int, int)
     */
    public CompletableFuture<List<T>> findAll() {
        return findAll(1000, 0);
    }

    /**
     * Asynchronously retrieves a paginated window of entities from the database.
     *
     * @param limit  the maximum number of rows to return in the query response window
     * @param offset the number of leading rows to skip before collecting records
     * @return a {@link CompletableFuture} supplying a {@link List} of the mapped entity results found
     */
    public CompletableFuture<List<T>> findAll(int limit, int offset) {
        String query = "SELECT * FROM " + tableName + " LIMIT ? OFFSET ?";
        return queryManager.queryForList(query, this::mapRow, limit, offset);
    }

    /**
     * Asynchronously inserts a new entity instance into the database.
     * <p>
     * Fields designated as {@code autoincrement = true} within their {@link Column} annotation
     * parameters are automatically omitted from the generated query structure to allow the underlying
     * database system to handle key sequence generation.
     * </p>
     *
     * @param entity the entity instance whose values are to be committed
     * @return a {@link CompletableFuture} supplying the number of database rows affected (typically 1)
     */
    public CompletableFuture<Integer> insert(T entity) {
        return queryManager.executeUpdate(insertQuery, extractValues(entity, false));
    }

    /**
     * Asynchronously updates all non-primary-key field data of an existing row matching the entity's primary key identifier.
     *
     * @param entity the entity instance containing the updated target values
     * @return a {@link CompletableFuture} supplying the number of database rows affected
     */
    public CompletableFuture<Integer> update(T entity) {
        return queryManager.executeUpdate(updateQuery, extractValues(entity, true));
    }

    /**
     * Asynchronously executes a single atomic insert-or-update (`UPSERT`) statement via an
     * {@code ON DUPLICATE KEY UPDATE} database query syntax structure.
     * <p>
     * This methodology combines insertion branching rules safely within a single database server round-trip.
     * It is highly recommended for highly concurrent save workflows, such as updating state objects in plugin architectures.
     * </p>
     *
     * @param entity the entity instance to insert or update
     * @return a {@link CompletableFuture} supplying the number of database rows affected (1 for an insert, 2 for an update)
     */
    public CompletableFuture<Integer> upsert(T entity) {
        return queryManager.executeUpdate(upsertQuery, extractValues(entity, false));
    }

    /**
     * Asynchronously inserts a collection of entities collectively in a single execution operation batch block.
     * <p>
     * Rather than triggering separate structural loops or network round-trips for every item, this method builds
     * a multi-row statement dynamically (e.g., {@code INSERT INTO table VALUES (?,?), (?,?)}).
     * </p>
     *
     * @param entities the {@link List} of entities to be inserted into the database
     * @return a {@link CompletableFuture} supplying the total number of database rows inserted or affected
     */
    public CompletableFuture<Integer> insertBatch(List<T> entities) {
        if (entities.isEmpty()) return CompletableFuture.completedFuture(0);

        List<Field> fields = columnFields();
        String cols = fields.stream()
                .map(f -> f.getAnnotation(Column.class).value())
                .collect(Collectors.joining(", "));
        String rowPlaceholder = "(" + fields.stream().map(f -> "?").collect(Collectors.joining(", ")) + ")";
        String allRows = String.join(", ", Collections.nCopies(entities.size(), rowPlaceholder));

        Object[] params = entities.stream()
                .flatMap(e -> Arrays.stream(extractValues(e, false)))
                .toArray();

        return queryManager.executeUpdate(
                "INSERT INTO " + tableName + " (" + cols + ") VALUES " + allRows, params
        );
    }

    /**
     * Asynchronously deletes a row record from the database matching the provided primary key identifier.
     *
     * @param id the primary key identifier of the record to remove
     * @return a {@link CompletableFuture} supplying the number of database rows affected
     */
    public CompletableFuture<Integer> delete(Object id) {
        return queryManager.executeUpdate(
                "DELETE FROM " + tableName + " WHERE " + primaryKeyColumn + " = ?", id
        );
    }

    /**
     * Synchronously builds and creates the target database table if it does not already exist.
     * <p>
     * This utility mapping method should typically be invoked exactly once during the initialization
     * or startup routine phase of a plugin lifecycle.
     * </p>
     */
    public void createTable() {
        queryManager.execute(TableSchemaBuilder.build(type));
    }

    // -------------------------------------------------------------------------
    // Protected — subclasses can override mapRow for performance-critical paths
    // -------------------------------------------------------------------------

    /**
     * Maps the active row of a SQL {@link ResultSet} to a new concrete Java object instance of type {@code T}.
     * <p>
     * The base implementation relies entirely on reflection by executing a parameterless default constructor
     * and sequentially mapping fields via column mappings. Subclasses can safely override this method to use explicit
     * setter parameters or builder pipelines to maximize throughput performance-critical execution loops.
     * </p>
     *
     * @param rs the open JDBC {@link ResultSet} positioned at the target mapping row
     * @return a mapped instance of the domain object entity
     * @throws SQLException if a database mapping error occurs, or if reflection initialization fails
     */
    protected T mapRow(ResultSet rs) throws SQLException {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            for (Field field : cachedAllColumnFields) {
                Column col = field.getAnnotation(Column.class);
                field.set(instance, rs.getObject(col.value()));
            }
            return instance;
        } catch (Exception e) {
            throw new SQLException("Failed to map row to " + type.getSimpleName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    /**
     * Extracts the database table name configuration from the type's {@link Table} annotation structural properties.
     */
    private String resolveTableName(Class<T> type) {
        Table table = type.getAnnotation(Table.class);
        if (table == null)
            throw new IllegalArgumentException(type.getSimpleName() + " is missing @Table");
        return table.value();
    }

    /**
     * Searches declared class fields via reflection to locate and identify the explicit primary key column definition.
     */
    private String resolvePrimaryKeyColumn() {
        for (Field field : type.getDeclaredFields()) {
            Column col = field.getAnnotation(Column.class);
            if (col != null && col.primaryKey()) return col.value();
        }
        throw new IllegalStateException(type.getSimpleName() + " has no field with @Column(primaryKey = true)");
    }

    /**
     * Reflectively constructs an internal list of fields declaring the {@link Column} annotation,
     * modifying field security access properties when necessary.
     *
     * @param skipAutoIncrement if true, fields containing an auto-incrementing designation parameter are skipped
     */
    private List<Field> buildColumnFields(boolean skipAutoIncrement) {
        List<Field> result = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            Column col = field.getAnnotation(Column.class);
            if (col == null) continue;
            if (skipAutoIncrement && col.autoincrement()) continue;
            field.setAccessible(true);
            result.add(field);
        }
        return result;
    }

    /**
     * Constructs the static pre-compiled SQL string template used for inserting record entries.
     */
    private String buildInsertQuery() {
        List<Field> fields = columnFields();
        return buildInsertBase(fields);
    }

    /**
     * Constructs the static pre-compiled SQL string template used for updating matching rows.
     */
    private String buildUpdateQuery() {
        List<Field> fields = columnFields(); // exclude autoincrement
        String set = fields.stream()
                .map(f -> {
                    String col = f.getAnnotation(Column.class).value();
                    return col + " = ?";
                })
                .collect(Collectors.joining(", "));
        return "UPDATE " + tableName + " SET " + set + " WHERE " + primaryKeyColumn + " = ?";
    }

    /**
     * Constructs the static pre-compiled SQL string template used for processing insert-or-update transactions.
     */
    private String buildUpsertQuery() {
        List<Field> fields = columnFields();
        String onDuplicate = fields.stream()
                .filter(f -> !f.getAnnotation(Column.class).primaryKey())
                .map(f -> {
                    String col = f.getAnnotation(Column.class).value();
                    return col + " = VALUES(" + col + ")";
                })
                .collect(Collectors.joining(", "));
        return buildInsertBase(fields) + " ON DUPLICATE KEY UPDATE " + onDuplicate;
    }

    /**
     * Helper mapping framework utilized to build standardized parameter insert statements.
     */
    private String buildInsertBase(List<Field> fields) {
        String cols = fields.stream()
                .map(f -> f.getAnnotation(Column.class).value())
                .collect(Collectors.joining(", "));
        String placeholders = fields.stream()
                .map(f -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
    }

    /**
     * Extracts instance field values from an entity instance target into an ordered parameters array.
     *
     * @param entity the entity instance containing data to inspect
     * @param pkLast if true, the primary key parameter value is appended at the very end of the collection array (required for UPDATE workflows)
     * @return an ordered array of query parameter values
     */
    private Object[] extractValues(T entity, boolean pkLast) {
        List<Object> values = new ArrayList<>();
        Object pkValue = null;
        try {
            for (Field field : type.getDeclaredFields()) {
                Column col = field.getAnnotation(Column.class);
                if (col == null || col.autoincrement()) continue;
                if (pkLast && col.primaryKey()) {
                    pkValue = field.get(entity);
                } else {
                    values.add(field.get(entity));
                }
            }
            if (pkLast) values.add(pkValue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to extract values from " + type.getSimpleName(), e);
        }
        return values.toArray();
    }

    /**
     * Retrieves the list of cached fields mapped to database columns, omitting auto-incremented fields.
     *
     * @return a unmodifiable structure reference of applicable column {@link Field} elements
     */
    @NotNull
    private List<Field> columnFields() {
        return cachedColumnFields;
    }
}