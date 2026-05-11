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

public abstract class AbstractDao<T> {

    protected final QueryManager queryManager;
    private final Class<T> type;
    private final String tableName;

    public AbstractDao(QueryManager queryManager, Class<T> type) {
        this.queryManager = queryManager;
        this.type = type;
        this.tableName = retrieveTableName(type);
    }

    public CompletableFuture<T> findByUUID(String uuid) {
        String query = "SELECT * FROM " + tableName + " WHERE uuid = ?";
        return queryManager.queryForValue(query, this::mapRow, uuid);
    }

    public CompletableFuture<List<T>> findAll() {
        String query = "SELECT * FROM " + tableName;
        return queryManager.queryForList(query, this::mapRow);
    }

    public CompletableFuture<Integer> insert(T entity) {
        String query = buildInsertQuery();
        Object[] params = extractValues(entity);
        return queryManager.executeUpdate(query, params);
    }

    public CompletableFuture<Integer> update(T entity) {
        String query = buildUpdateQuery();
        Object[] params = extractValues(entity);
        return queryManager.executeUpdate(query, params);
    }

    public CompletableFuture<Integer> delete(String uuid) {
        String query = "DELETE FROM " + tableName + " WHERE uuid = ?";
        return queryManager.executeUpdate(query, uuid);
    }

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
