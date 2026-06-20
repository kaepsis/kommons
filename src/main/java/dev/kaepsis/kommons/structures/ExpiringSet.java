package dev.kaepsis.kommons.structures;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A thread-safe, set-like data structure where elements automatically expire and
 * are removed after a configured Time-To-Live (TTL).
 * * <p>This implementation is backed by a {@link ConcurrentHashMap}. It employs a dual
 * approach to expiration:
 * <ul>
 * <li><b>Passive Expiration:</b> Items are checked and lazily removed during read operations
 * like {@link #contains(Object)}.</li>
 * <li><b>Active Expiration:</b> A background daemon thread periodically sweeps the set
 * to clean up expired entries.</li>
 * </ul>
 * * <p>Note: To prevent resource leaks from the background scheduler, ensure that
 * {@link #shutdown()} is invoked when this data structure is no longer needed.
 * * @param <T> the type of elements maintained by this set
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExpiringSet<T> {

    private final ConcurrentHashMap<T, Long> map = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner;

    /**
     * Constructs an {@code ExpiringSet} with the specified Time-To-Live (TTL) for its elements.
     * <p>
     * Initializes a background daemon thread that runs a cleanup task at a fixed rate.
     * The cleanup interval is dynamically calculated as half of the TTL, with a minimum
     * floor of 1 second (1000ms).
     *
     * @param ttlSeconds the duration in seconds that an item remains valid after being added
     * @throws IllegalArgumentException if {@code ttlSeconds} is less than or equal to zero
     */
    public ExpiringSet(long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
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
     * Adds the specified item to the set, setting or resetting its expiration time.
     * <p>
     * If the item is already present, its expiration time is overwritten/refreshed
     * to extend its lifespan by the configured TTL from the current system time.
     *
     * @param item the element to be added
     */
    public void add(T item) {
        map.put(item, System.currentTimeMillis() + ttlMillis);
    }

    /**
     * Adds the specified item to the set only if it is not already present and active.
     * <p>
     * An item is considered absent if it does not exist in the set, or if it exists
     * but has already expired based on the current system time. If the existing item
     * is expired, this method atomically updates its expiration time.
     *
     * @param item the element to be added if absent
     * @return {@code true} if the item was successfully added or its expired lease was refreshed;
     * {@code false} if a valid, unexpired item already exists in the set
     */
    public boolean addIfAbsent(T item) {
        long now = System.currentTimeMillis();
        long expiry = now + ttlMillis;
        return map.compute(item, (_, oldExpiry) ->  {
            if (oldExpiry == null || oldExpiry < now) {
                return expiry;
            }
            return oldExpiry;
        }) == expiry;
    }

    /**
     * Checks if the set contains the specified item and guarantees that it is not expired.
     * <p>
     * This operation performs a passive cleanup: if the item is present but its TTL has
     * passed, it is immediately removed from the set and this method returns {@code false}.
     *
     * @param item the element whose presence in this set is to be tested
     * @return {@code true} if this set contains the specified element and it has not yet expired;
     * {@code false} otherwise
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
     * Explicitly removes the specified item from the set, regardless of whether it has expired or not.
     *
     * @param item the element to be removed
     */
    public void remove(T item) {
        map.remove(item);
    }

    /**
     * Returns an unmodifiable, point-in-time snapshot of all currently active (unexpired)
     * elements in the set.
     * <p>
     * This method triggers an immediate, synchronous cleanup of all expired entries before
     * capturing the snapshot to ensure maximum accuracy.
     *
     * @return an unmodifiable {@link Set} containing the active items at the time of the call
     */
    public Set<T> snapshot() {
        cleanup();
        return Set.copyOf(map.keySet());
    }

    /**
     * Synchronously iterates through the entire map and removes any entries whose
     * expiration timestamp is strictly less than the current system time.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue() < now);
    }

    /**
     * Shuts down the background cleanup scheduler immediately.
     * <p>
     * This method should be called when the set is no longer needed to prevent
     * thread/resource leaks. Ongoing tasks are interrupted, and scheduled tasks
     * are cancelled.
     */
    public void shutdown() {
        cleaner.shutdownNow();
    }

}