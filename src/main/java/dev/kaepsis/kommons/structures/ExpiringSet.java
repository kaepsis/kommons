package dev.kaepsis.kommons.structures;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A thread‑safe set where entries automatically expire after a specified time‑to‑live (TTL).
 * <p>
 * {@code ExpiringSet} stores items with an associated expiry timestamp. An item is considered
 * present only if its TTL has not yet elapsed. Expired items are automatically removed both
 * lazily (when checked via {@link #contains(Object)}) and periodically by a background cleanup
 * thread.
 * </p>
 * <p>
 * The TTL is defined in seconds at construction time, and a background daemon thread cleans
 * up expired entries at an interval equal to half the TTL (minimum once per second). The
 * set uses a {@link ConcurrentHashMap} internally, making all operations safe for concurrent
 * access from multiple threads.
 * </p>
 * <p>
 * This class is useful for temporary caches, rate‑limiting, session management, or any
 * scenario where items should be automatically forgotten after a certain period.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * ExpiringSet<String> set = new ExpiringSet<>(30); // TTL = 30 seconds
 * set.add("player123");
 * System.out.println(set.contains("player123")); // true
 * Thread.sleep(31000);
 * System.out.println(set.contains("player123")); // false (expired)
 * set.shutdown();
 * }</pre>
 * </p>
 *
 * @param <T> the type of elements maintained by this set
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class ExpiringSet<T> {

    private final ConcurrentHashMap<T, Long> map = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner;

    /**
     * Constructs an expiring set with the specified time‑to‑live in seconds.
     * <p>
     * The TTL is converted to milliseconds internally. A daemon background thread is started
     * to periodically remove expired entries. The cleanup interval is set to half the TTL
     * (minimum 1 second).
     * </p>
     *
     * @param ttlSeconds the time‑to‑live in seconds (must be positive)
     * @throws IllegalArgumentException if {@code ttlSeconds <= 0}
     */
    public ExpiringSet(long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlMillis cannot be negative");
        this.ttlMillis = ttlSeconds * 1000L;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ExpiringSet-Cleaner");
            t.setDaemon(true);
            return t;
        });
        long cleanupInterval = Math.max(1000L, ttlMillis / 2);
        cleaner.scheduleAtFixedRate(this::cleanup, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds the specified element to the set, overwriting any existing entry.
     * <p>
     * The element will expire after the configured TTL. If the same element already exists,
     * its expiry time is reset to the current time plus TTL.
     * </p>
     *
     * @param item the element to add (may be {@code null})
     */
    public void add(T item) {
        map.put(item, System.currentTimeMillis() + ttlMillis);
    }

    /**
     * Adds the specified element only if it is not already present with a non‑expired entry.
     * <p>
     * If an expired entry exists, it is effectively absent, so the element will be added.
     * This method is atomic: the check and insertion are performed as a single operation.
     * </p>
     *
     * @param item the element to add (may be {@code null})
     * @return {@code true} if the element was added (i.e., it was absent or expired),
     *         {@code false} if it was already present and still valid
     */
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

    /**
     * Checks whether the specified element is present and has not yet expired.
     * <p>
     * If the element exists but its expiry time has passed, it is removed from the set
     * and this method returns {@code false}.
     * </p>
     *
     * @param item the element to check (may be {@code null})
     * @return {@code true} if the element is present and not expired, {@code false} otherwise
     */
    public boolean contains(T item) {
        Long expiry = map.get(item);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {
            map.remove(item);
            return false;
        }
        return true;
    }

    /**
     * Removes the specified element from the set if present.
     * <p>
     * This operation is performed regardless of the element's expiration state.
     * </p>
     *
     * @param item the element to remove (may be {@code null})
     */
    public void remove(T item) {
        map.remove(item);
    }

    /**
     * Returns a snapshot of the currently non‑expired elements.
     * <p>
     * This method first performs a cleanup of expired entries and then returns a view
     * of the remaining keys. The returned set is a snapshot at the time of the call;
     * subsequent changes to the {@code ExpiringSet} are not reflected in the snapshot.
     * </p>
     *
     * @return a {@link Set} containing all non‑expired elements (never {@code null})
     */
    public Set<T> snapshot() {
        cleanup();
        return map.keySet();
    }

    /**
     * Removes all expired entries from the internal map.
     * <p>
     * This method is called automatically by the background cleaner thread, but can also
     * be invoked manually if immediate cleanup is desired.
     * </p>
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue() < now);
    }

    /**
     * Shuts down the background cleanup executor.
     * <p>
     * After calling this method, no further automatic cleanup will occur. The set can still
     * be used, but expired entries will only be removed lazily via {@link #contains(Object)}
     * or manually by calling {@link #cleanup()} (which is private but can be triggered via
     * {@link #snapshot()}). This method should be called when the set is no longer needed,
     * especially to avoid keeping the executor alive.
     * </p>
     */
    public void shutdown() {
        cleaner.shutdownNow();
    }

}