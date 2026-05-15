package dev.kaepsis.kommons.database.interfaces;

import java.sql.SQLException;

/**
 * A functional interface that represents a function which may throw a {@link SQLException}.
 * <p>
 * This interface is primarily used in database operations where a function applied
 * to an input might fail due to SQL errors. It allows lambda expressions or method
 * references to be used in contexts that require exception transparency without
 * wrapping checked exceptions in {@code RuntimeException}.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * ISQLFunction<ResultSet, Player> mapper = rs -> {
 *     Player p = new Player();
 *     p.setName(rs.getString("name"));
 *     return p;
 * };
 * }</pre>
 * </p>
 *
 * @param <T> the function input type
 * @param <R> the function result type
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
@FunctionalInterface
public interface ISQLFunction<T, R> {
    R apply(T input) throws SQLException;
}