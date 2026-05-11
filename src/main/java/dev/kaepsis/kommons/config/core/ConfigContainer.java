package dev.kaepsis.kommons.config.core;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class ConfigContainer {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Object> data = new LinkedHashMap<>();

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

    public Object getOrDefault(String key, Object defaultValue) {
        Object value = get(key);
        return value != null ? value : defaultValue;
    }

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

    public Map<String, Object> snapshot() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replace(Map<String, Object> newData) {
        lock.writeLock().lock();
        try {
            data.clear();
            data.putAll(newData);
        } finally {
            lock.writeLock().unlock();
        }
    }

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