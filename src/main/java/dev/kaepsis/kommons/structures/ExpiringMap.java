package dev.kaepsis.kommons.structures;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A thread-safe, map-like data structure where key-value pairs automatically expire
 * and are removed after a configured Time-To-Live (TTL).
 * <p>
 * This implementation wraps values inside an immutable {@link Entry} record and is backed
 * by a {@link ConcurrentHashMap}. It employs a hybrid expiration mechanism:
 * <ul>
 * <li><b>Passive Expiration:</b> Entries are evaluated and lazily removed during read operations
 * like {@link #get(Object)} and {@link #containsKey(Object)}.</li>
 * <li><b>Active Expiration:</b> A background daemon thread periodically sweeps the map
 * to clean up expired entries.</li>
 * </ul>
 * <p>
 * Note: To prevent resource leaks from the background scheduler, ensure that
 * {@link #shutdown()} is invoked when this data structure is no longer needed.
 * * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExpiringMap<K, V> {

    /**
     * An internal immutable container holding the user value and its absolute expiration epoch millisecond timestamp.
     *
     * @param <V>    the type of the wrapped value
     * @param value  the actual value associated with the key
     * @param expiry the millisecond epoch timestamp at which this entry expires
     */
    private record Entry<V>(V value, long expiry) {}

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final ScheduledExecutorService cleaner;

    /**
     * Constructs an {@code ExpiringMap} with the specified Time-To-Live (TTL) for its entries.
     * <p>
     * Initializes a background daemon thread that runs a cleanup task at a fixed rate.
     * The cleanup interval is dynamically calculated as half of the TTL, with a minimum
     * floor of 1 second (1000ms).
     *
     * @param ttlSeconds the duration in seconds that an entry remains valid after insertion
     * @throws IllegalArgumentException if {@code ttlSeconds} is less than or equal to zero
     */
    public ExpiringMap(long ttlSeconds) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.ttlMillis = ttlSeconds * 1000L;
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ExpiringMap-Cleaner");
            t.setDaemon(true);
            return t;
        });
        long cleanupInterval = Math.max(1000L, ttlMillis / 2);
        cleaner.scheduleAtFixedRate(this::cleanup, cleanupInterval, cleanupInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Associates the specified value with the specified key in this map, setting or resetting
     * its expiration time.
     * <p>
     * If the map previously contained a mapping for the key, the old value and its remaining
     * lifetime are overwritten/refreshed by the configured TTL from the current system time.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    public void put(K key, V value) {
        this.map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * Returns an {@link Optional} containing the value to which the specified key is mapped,
     * provided the entry has not yet expired.
     * <p>
     * This operation performs a passive cleanup: if the key is present but its TTL has
     * passed, the entry is atomically removed from the map (if it hasn't been modified since),
     * and this method returns an empty {@code Optional}.
     *
     * @param key the key whose associated value is to be returned
     * @return an {@link Optional} containing the active value if present and unexpired;
     * otherwise an empty {@link Optional}
     */
    public Optional<V> get(K key) {
        Entry<V> entry = this.map.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expiry() < System.currentTimeMillis()) {
            this.map.remove(key, entry);
            return Optional.empty(); // Fixed logic bug: was returning Optional.of()
        }
        return Optional.of(entry.value());
    }

    /**
     * Returns {@code true} if this map contains a mapping for the specified key and
     * the entry is still actively valid.
     * <p>
     * This method relies on {@link #get(Object)} internally, inheriting its passive cleanup behavior.
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a valid, unexpired mapping for the specified key;
     * {@code false} otherwise
     */
    public boolean containsKey(K key) {
        return get(key).isPresent();
    }

    /**
     * Associates the specified key with the given value only if the key is not already
     * mapped to a valid, unexpired entry.
     * <p>
     * A key is considered absent if it does not exist in the map, or if it exists
     * but its lease has already expired based on the current system time. If an existing
     * entry is expired, it is atomically replaced.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return {@code true} if the value was successfully put or replaced an expired entry;
     * {@code false} if a valid, unexpired entry already exists for the key
     */
    public boolean putIfAbsent(K key, V value) {
        long now = System.currentTimeMillis();
        Entry<V> newEntry = new Entry<>(value, now + ttlMillis);
        Entry<V> result = map.compute(key, (_, old) -> {
            if (old == null || old.expiry() < now) return newEntry;
            return old;
        });
        return result == newEntry;
    }

    /**
     * Explicitly removes the mapping for a key from this map if it is present,
     * regardless of whether it has expired or not.
     *
     * @param key key whose mapping is to be removed from the map
     */
    public void remove(K key) {
        this.map.remove(key);
    }

    /**
     * Returns an unmodifiable, point-in-time snapshot of all currently active (unexpired)
     * key-value mappings.
     * <p>
     * This method triggers an immediate, synchronous cleanup of all expired entries before
     * collecting the results into a plain map to ensure maximum accuracy.
     *
     * @return an unmodifiable {@link Map} containing the active keys and their unpacked values
     */
    public Map<K, V> snapshot() {
        cleanup();
        return map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().value()
                ));
    }

    /**
     * Synchronously sweeps the entire backing map and removes any entries whose
     * expiration timestamp is strictly less than the current system time.
     */
    private void cleanup() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue().expiry() < now);
    }

    /**
     * Shuts down the background cleanup scheduler immediately.
     * <p>
     * This method should be called when the map is no longer needed to prevent
     * thread/resource leaks. Ongoing tasks are interrupted, and scheduled tasks
     * are cancelled.
     */
    public void shutdown() {
        cleaner.shutdownNow();
    }

}