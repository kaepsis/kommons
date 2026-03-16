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

public class QueryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryManager.class);

    private final HikariDataSource dataSource;
    private final Executor asyncExecutor;

    public QueryManager(HikariDataSource dataSource, Executor asyncExecutor) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    public QueryManager(HikariDataSource dataSource) {
        this(dataSource, null);
    }

    private static void setParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    public CompletableFuture<Integer> executeUpdate(String query, Object... params) {
        return supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                setParams(stmt, params);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Errore durante executeUpdate: {}", query, e);
                throw e;
            }
        });
    }

    public CompletableFuture<Void> execute(String query, Object... params) {
        return executeUpdate(query, params).thenApply(rows -> null);
    }

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
                LOGGER.error("Errore durante queryForValue: {}", query, e);
                throw e;
            }
        });
    }

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
                LOGGER.error("Errore durante queryForList: {}", query, e);
                throw e;
            }
            return list;
        });
    }

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
                LOGGER.error("Errore durante la transazione", e);
                throw e;
            }
        });
    }

    private <U> CompletableFuture<U> supplyAsync(SupplierWithException<U> supplier) {
        CompletableFuture<U> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        if (asyncExecutor != null) {
            asyncExecutor.execute(task);
        } else {
            CompletableFuture.runAsync(task);
        }
        return future;
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }
}