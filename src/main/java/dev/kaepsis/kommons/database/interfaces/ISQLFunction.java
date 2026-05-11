package dev.kaepsis.kommons.database.interfaces;

import java.sql.SQLException;

@FunctionalInterface
public interface ISQLFunction<T, R> {
    R apply(T input) throws SQLException;
}
