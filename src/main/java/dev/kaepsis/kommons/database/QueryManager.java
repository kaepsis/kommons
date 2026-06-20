package dev.kaepsis.kommons.database;

import com.zaxxer.hikari.HikariDataSource;
import dev.kaepsis.kommons.database.interfaces.ISQLFunction;
import dev.kaepsis.kommons.database.interfaces.SupplierWithException;
import dev.kaepsis.kommons.database.interfaces.TransactionFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * A reactive, asynchronous query manager for database operations using HikariCP.
 * <p>
 * {@code QueryManager} wraps a {@link HikariDataSource} and an optional {@link Executor}
 * to perform database queries asynchronously. All methods return {@link CompletableFuture},
 * allowing non‑blocking database access. The manager supports:
 * <ul>
 *   <li>Updates (INSERT, UPDATE, DELETE) via {@link #executeUpdate(String, Object...)}</li>
 *   <li>Single‑value queries via {@link #queryForValue(String, ISQLFunction, Object...)}</li>
 *   <li>Multi‑row queries via {@link #queryForList(String, ISQLFunction, Object...)}</li>
 *   <li>Programmatic transactions via {@link #executeTransaction(TransactionFunction)}</li>
 * </ul>
 * </p>
 * <p>
 * All database operations are executed on the provided {@code Executor} (or a default
 * ForkJoinPool if none is supplied). This ensures that the main server thread is never blocked.
 * </p>
 * <p>
 * The manager is implemented as a Java {@code record}, making it immutable and transparent.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * HikariDataSource dataSource = ...;
 * Executor executor = ForkJoinPool.commonPool();
 * QueryManager qm = new QueryManager(dataSource, executor);
 *
 * qm.executeUpdate("INSERT INTO users(name) VALUES(?)", "Alice")
 *   .thenAccept(rows -> System.out.println("Inserted " + rows + " rows"));
 *
 * qm.queryForValue("SELECT name FROM users WHERE id = ?", rs -> rs.getString("name"), 1)
 *   .thenAccept(name -> System.out.println("Name: " + name));
 * }</pre>
 * </p>
 *
 * @param dataSource    the HikariCP connection pool (must not be {@code null})
 * @param asyncExecutor the executor for asynchronous tasks (if {@code null}, the default
 *                      {@code CompletableFuture} mechanism is used)
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public record QueryManager(HikariDataSource dataSource, Executor asyncExecutor) {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryManager.class);

    /**
     * Creates a new query manager with the given data source and no custom executor.
     * <p>
     * When no executor is provided, {@code CompletableFuture} will use the common
     * ForkJoinPool (or the default async execution mechanism).
     * </p>
     *
     * @param dataSource the HikariCP connection pool (must not be {@code null})
     */
    public QueryManager(HikariDataSource dataSource) {
        this(dataSource, null);
    }

    private static void setParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    /**
     * Executes an update (INSERT, UPDATE, DELETE) asynchronously.
     *
     * @param query  the SQL query (with placeholders {@code ?})
     * @param params the parameters to bind to the placeholders
     * @return a {@code CompletableFuture} that completes with the number of affected rows
     */
    public CompletableFuture<Integer> executeUpdate(String query, Object... params) {
        return supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                setParams(stmt, params);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("executeUpdate failed: {}", query, e);
                throw e;
            }
        });
    }

    /**
     * Executes an update query and ignores the result.
     * <p>
     * This method is a fire‑and‑forget version of {@link #executeUpdate}. The returned
     * {@code CompletableFuture} is not used, but the operation is still asynchronous.
     * </p>
     *
     * @param query  the SQL query (with placeholders)
     * @param params the parameters for the query
     */
    public void execute(String query, Object... params) {
        executeUpdate(query, params)
                .exceptionally(err -> {
                    LOGGER.error("execute failed: {}", query, err);
                    return null;
                });
    }

    /**
     * Executes a query that returns at most one row, mapping the result with the given extractor.
     *
     * @param <T>       the type of the result
     * @param query     the SQL query (with placeholders)
     * @param extractor a function that maps a {@link ResultSet} to an object of type {@code T}
     * @param params    the parameters for the query
     * @return a {@code CompletableFuture} that completes with the extracted value,
     *         or {@code null} if no row was returned
     *
     * <p>Example usage:
     * <pre>{@code
     * queryManager.queryForValue(
     *     "SELECT name FROM players WHERE uuid = ?",
     *     rs -> rs.getString("name"),
     *     uuid
     * ).thenAccept(name -> {
     *     if (name != null) player.sendMessage("Found: " + name);
     * });
     * }</pre>
     * </p>
     */
    public <T> CompletableFuture<T> queryForValue(String query, ISQLFunction<ResultSet, T> extractor, Object... params) {
        return supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                setParams(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return extractor.apply(rs);
                    }
                    return null;
                }
            } catch (SQLException e) {
                LOGGER.error("queryForValue failed: {}", query, e);
                throw e;
            }
        });
    }

    /**
     * Executes a query that may return multiple rows, mapping each row to an object.
     *
     * @param <T>       the type of the result elements
     * @param query     the SQL query (with placeholders)
     * @param extractor a function that maps a {@link ResultSet} to an object of type {@code T}
     * @param params    the parameters for the query
     * @return a {@code CompletableFuture} that completes with a list of mapped objects
     *         (never {@code null}; empty list if no rows)
     *
     * <p>Example usage:
     * <pre>{@code
     * queryManager.queryForList(
     *     "SELECT * FROM players WHERE online = ?",
     *     rs -> new Player(rs.getString("uuid"), rs.getString("name"), rs.getInt("level")),
     *     true
     * ).thenAccept(players -> {
     *     players.forEach(p -> Bukkit.broadcastMessage(p.getName()));
     * });
     * }</pre>
     * </p>
     */
    public <T> CompletableFuture<List<T>> queryForList(String query, ISQLFunction<ResultSet, T> extractor, Object... params) {
        return supplyAsync(() -> {
            List<T> list = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                setParams(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(extractor.apply(rs));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("queryForList failed: {}", query, e);
                throw e;
            }
            return list;
        });
    }

    /**
     * Executes a transaction asynchronously, providing a managed {@link Connection}.
     * <p>
     * The method automatically handles auto‑commit: it sets the connection to
     * {@code autoCommit = false} before executing the transaction function,
     * commits if the function completes without exceptions, and rolls back if
     * any exception is thrown. After the transaction finishes (successfully or not),
     * auto‑commit is restored to {@code true}.
     * </p>
     * <p>
     * The function receives the active connection; any statements created within
     * will participate in the transaction.
     * </p>
     *
     * @param <T>         the return type of the transaction
     * @param transaction a function that receives a {@link Connection} and returns a result
     * @return a {@code CompletableFuture} that completes with the result of the transaction
     */
    public <T> CompletableFuture<T> executeTransaction(TransactionFunction<T> transaction) {
        return supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    T result = transaction.apply(conn);
                    conn.commit();
                    return result;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                LOGGER.error("Transaction update failed", e);
                throw e;
            }
        });
    }

    private <U> CompletableFuture<U> supplyAsync(SupplierWithException<U> supplier) {
        if (asyncExecutor != null) {
            CompletableFuture<U> future = new CompletableFuture<>();
            asyncExecutor.execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}