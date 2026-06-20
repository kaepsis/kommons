package dev.kaepsis.kommons.database.interfaces;

import java.sql.Connection;

/**
 * A functional interface that represents a function operating on a database
 * {@link Connection} and returning a result, potentially throwing any exception.
 * <p>
 * This interface is designed for transactional database operations where a
 * connection is provided (usually by a transaction manager) and the function
 * body can execute multiple statements, commit/rollback logic being handled
 * externally. Any exception thrown will typically trigger a rollback.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * TransactionFunction<Integer> transfer = conn -> {
 *     try (PreparedStatement stmt = conn.prepareStatement("UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
 *         stmt.setInt(1, 100);
 *         stmt.setInt(2, 1);
 *         stmt.executeUpdate();
 *         // ... second update
 *         return 1;
 *     }
 * };
 * }</pre>
 * </p>
 *
 * @param <T> the return type of the transactional operation
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
@FunctionalInterface
public interface TransactionFunction<T> {
    T apply(Connection connection) throws Exception;
}