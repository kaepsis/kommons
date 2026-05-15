package dev.kaepsis.kommons.database.interfaces;

/**
 * A functional interface that represents a supplier which may throw any {@code Exception}.
 * <p>
 * This interface is useful for database operations that need to lazily provide a value
 * and where checked exceptions (e.g., {@code SQLException}, {@code IOException}) are
 * anticipated. It allows the caller to handle the exception appropriately rather than
 * being forced to catch it inside the lambda.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * SupplierWithException<Connection> connectionSupplier = () -> {
 *     return DriverManager.getConnection(url, user, password);
 * };
 * }</pre>
 * </p>
 *
 * @param <T> the type of the result supplied
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
@FunctionalInterface
public interface SupplierWithException<T> {
    T get() throws Exception;
}