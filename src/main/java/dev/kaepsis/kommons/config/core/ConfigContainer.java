package dev.kaepsis.kommons.config.core;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * A thread‑safe, nested map container for configuration data.
 * <p>
 * {@code ConfigContainer} stores key‑value pairs in a hierarchical structure similar to YAML.
 * Keys use dot notation (e.g., {@code "database.host"}) to represent nested maps. All read and
 * write operations are guarded by a {@link ReadWriteLock}, allowing concurrent reads but
 * exclusive writes.
 * </p>
 * <p>
 * The container is typically used as the runtime representation of a configuration file,
 * supporting retrieval, modification, and snapshot creation. It does not perform any type
 * conversion beyond what is stored; callers are responsible for casting retrieved objects.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConfigContainer container = new ConfigContainer();
 * container.set("server.name", "MyServer");
 * container.set("server.port", 25565);
 *
 * String name = (String) container.get("server.name");        // "MyServer"
 * int port = (int) container.getOrDefault("server.port", 8080);
 * Set<String> keys = container.getKeys("server");             // ["name", "port"]
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class ConfigContainer {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Object> data = new LinkedHashMap<>();

    /**
     * Retrieves the value associated with the given dot‑separated key.
     * <p>
     * If any part of the key path does not exist, or if an intermediate part is not a {@code Map},
     * this method returns {@code null}.
     * </p>
     *
     * @param key the dot‑separated key (e.g., {@code "database.connection.url"})
     * @return the value at the specified key, or {@code null} if not found
     */
    public Object get(String key) {
        lock.readLock().lock();
        try {
            String[] parts = key.split("\\.");
            Map<String, Object> currentMap = data;
            Object result = null;
            for (int i = 0; i < parts.length; i++) {
                result = currentMap.get(parts[i]);
                if (result == null) {
                    return null;
                }
                if (i < parts.length - 1) {
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> next = (Map<String, Object>) result;
                        currentMap = next;
                    } else {
                        return null;
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the value for the given key, or a default value if the key is not present.
     *
     * @param key          the dot‑separated key
     * @param defaultValue the value to return if the key is not found
     * @return the stored value, or {@code defaultValue} if the key does not exist
     */
    public Object getOrDefault(String key, Object defaultValue) {
        Object value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Stores a value at the specified dot‑separated key, creating intermediate maps as needed.
     * <p>
     * If an intermediate key already exists but is not a {@code Map}, it is silently replaced
     * with a new {@link LinkedHashMap}. The operation is performed under a write lock.
     * </p>
     *
     * @param key   the dot‑separated key under which to store the value
     * @param value the value to store (may be any object, including nested maps)
     */
    public void set(String key, Object value) {
        lock.writeLock().lock();
        try {
            String[] parts = key.split("\\.");
            Map<String, Object> currentMap = data;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = currentMap.get(parts[i]);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    currentMap.put(parts[i], next);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> nextMap = (Map<String, Object>) next;
                currentMap = nextMap;
            }
            currentMap.put(parts[parts.length - 1], value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a shallow copy of the entire data map.
     * <p>
     * The returned map is a new {@link LinkedHashMap} that contains the same keys and values
     * as the internal data. However, nested maps are <strong>not</strong> deep‑copied;
     * modifications to those nested maps will affect the container unless the caller
     * explicitly deep‑copies them.
     * </p>
     *
     * @return a new {@code LinkedHashMap} containing the current data
     */
    public Map<String, Object> snapshot() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Replaces the entire content of this container with the given map.
     * <p>
     * The existing data is cleared, then all entries from {@code newData} are added.
     * This operation is performed under a write lock.
     * </p>
     *
     * @param newData the map that will become the new data source (may be {@code null};
     *                if {@code null}, the container is cleared)
     */
    public void replace(Map<String, Object> newData) {
        lock.writeLock().lock();
        try {
            data.clear();
            if (newData != null) {
                data.putAll(newData);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks whether the given dot‑separated key exists in the container.
     *
     * @param key the dot‑separated key to check
     * @return {@code true} if the key is present, {@code false} otherwise
     */
    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            String[] parts = key.split("\\.");
            Map<String, Object> currentMap = data;
            for (int i = 0; i < parts.length; i++) {
                Object value = currentMap.get(parts[i]);
                if (value == null) {
                    return false;
                }
                if (i < parts.length - 1) {
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> next = (Map<String, Object>) value;
                        currentMap = next;
                    } else {
                        return false;
                    }
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes the entry for the specified dot‑separated key.
     * <p>
     * If the key does not exist, this method does nothing. If an intermediate part
     * is not a {@code Map}, the removal is silently ignored. The operation is performed
     * under a write lock.
     * </p>
     *
     * @param key the dot‑separated key to remove
     */
    public void remove(String key) {
        lock.writeLock().lock();
        try {
            String[] parts = key.split("\\.");
            Map<String, Object> currentMap = data;
            for (int i = 0; i < parts.length - 1; i++) {
                Object next = currentMap.get(parts[i]);
                if (!(next instanceof Map)) return;
                @SuppressWarnings("unchecked")
                Map<String, Object> nextMap = (Map<String, Object>) next;
                currentMap = nextMap;
            }
            currentMap.remove(parts[parts.length - 1]);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a set of keys directly under the given path.
     * <p>
     * For example, if the container holds:
     * <pre>{@code
     * database:
     *   host: localhost
     *   port: 3306
     * }</pre>
     * then {@code getKeys("database")} returns {@code ["host", "port"]}.
     * </p>
     *
     * @param path the dot‑separated path to the parent map; if {@code null} or empty,
     *             returns the keys at the root level
     * @return an immutable set of direct child keys; never {@code null}
     */
    public Set<String> getKeys(String path) {
        lock.readLock().lock();
        try {
            Object node = path == null || path.isEmpty() ? data : get(path);
            if (node instanceof Map<?, ?> map) {
                return map.keySet().stream()
                        .map(Object::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
            return Collections.emptySet();
        } finally {
            lock.readLock().unlock();
        }
    }
}