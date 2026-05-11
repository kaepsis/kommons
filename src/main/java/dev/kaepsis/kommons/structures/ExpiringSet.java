package dev.kaepsis.kommons.structures;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExpiringSet<T> {

    private final ConcurrentHashMap<T, Long> map = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner;

    public ExpiringSet(long ttlMillis) {
        if (ttlMillis <= 0) throw new IllegalArgumentException("ttlMillis cannot be negative");
        this.ttlMillis = ttlMillis * 1000L;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ExpiringSet-Cleaner");
            t.setDaemon(true);
            return t;
        });
        long cleanupInterval = Math.max(1000L, ttlMillis / 2);
        cleaner.scheduleAtFixedRate(this::cleanup, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    public void add(T item) {
        map.put(item, System.currentTimeMillis() + ttlMillis);
    }

    public boolean addIfAbsent(T item) {
        long now = System.currentTimeMillis();
        long expiry = now + ttlMillis;
        return map.compute(item, (k, oldExpiry) ->  {
           if (oldExpiry == null || oldExpiry < now) {
               return expiry;
           }
           return oldExpiry;
        }) == expiry;
    }

    public boolean contains(T item) {
        Long expiry = map.get(item);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            map.remove(item);
            return false;
        }
        return true;
    }

    public void remove(T item) {
        map.remove(item);
    }

    public Set<T> snapshot() {
        cleanup();
        return map.keySet();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue() < now);
    }

    public void shutdown() {
        cleaner.shutdownNow();
    }

}
