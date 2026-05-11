package dev.kaepsis.kommons.database.interfaces;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionFunction<T> {
    T apply(Connection connection) throws Exception;
}