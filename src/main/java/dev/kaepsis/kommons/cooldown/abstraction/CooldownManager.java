package dev.kaepsis.kommons.cooldown.abstraction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * An abstract manager for cooldowns associated with keys of type {@code K}.
 * <p>
 * This class provides a thread‑safe way to track cooldowns (temporary locks)
 * for arbitrary keys, such as player UUIDs, command names, or item types.
 * Cooldowns are stored in memory using a {@link ConcurrentHashMap} and expire
 * automatically after a specified duration. Expired entries are lazily removed
 * when checked via {@link #has(Object)} or {@link #getRemainingMillis(Object)},
 * and can also be bulk‑cleaned using {@link #cleanExpired()}.
 * </p>
 * <p>
 * Typical usage involves extending this class to create domain‑specific managers:
 * <pre>{@code
 * public class PlayerCooldownManager extends CooldownManager<UUID> {
 *     // optionally add convenience methods
 * }
 *
 * PlayerCooldownManager manager = new PlayerCooldownManager();
 * manager.set(playerId, 30, TimeUnit.SECONDS);
 * if (!manager.has(playerId)) {
 *     // player is not on cooldown
 * }
 * }</pre>
 * </p>
 * <p>
 * All methods are safe to call from multiple threads concurrently.
 * </p>
 *
 * @param <K> the type of keys used to identify cooldowns (e.g., {@code UUID}, {@code String})
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class CooldownManager<K> {

    protected final ConcurrentHashMap<K, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Sets a cooldown for the specified key.
     * <p>
     * The cooldown expires after the given duration measured in the provided time unit.
     * If a cooldown already exists for the same key, it is overwritten with the new expiry.
     * </p>
     *
     * @param key      the key to put on cooldown (must not be {@code null})
     * @param duration the length of the cooldown (must be non‑negative)
     * @param unit     the time unit of the duration (must not be {@code null})
     */
    public void set(K key, long duration, TimeUnit unit) {
        long expiry = System.currentTimeMillis() + unit.toMillis(duration);
        cooldowns.put(key, expiry);
    }

    /**
     * Checks whether the given key is currently on cooldown.
     * <p>
     * If the cooldown has expired, this method automatically removes the entry
     * and returns {@code false}. This ensures that expired cooldowns do not linger.
     * </p>
     *
     * @param key the key to check
     * @return {@code true} if the key is on cooldown (expiry time in the future),
     *         {@code false} otherwise (no cooldown or expired)
     */
    public boolean has(K key) {
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;
        if (expiry > System.currentTimeMillis()) return true;
        cooldowns.remove(key);
        return false;
    }

    /**
     * Returns the remaining time of the cooldown for the given key, in milliseconds.
     * <p>
     * If the key is not on cooldown or the cooldown has expired, this method returns
     * {@code 0}. Expired entries are automatically removed.
     * </p>
     *
     * @param key the key to query
     * @return the remaining milliseconds until the cooldown expires, or {@code 0} if
     *         no active cooldown exists
     */
    public long getRemainingMillis(K key) {
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining > 0) return remaining;
        cooldowns.remove(key);
        return 0;
    }

    /**
     * Manually removes any cooldown associated with the given key.
     * <p>
     * Does nothing if the key has no cooldown.
     * </p>
     *
     * @param key the key whose cooldown should be removed
     */
    public void remove(K key) {
        cooldowns.remove(key);
    }

    /**
     * Removes all expired cooldowns from the internal map.
     * <p>
     * This method iterates over all entries and removes those whose expiry time
     * is not greater than the current system time. It can be called periodically
     * to keep the map size under control, but expiration is also handled lazily
     * in {@link #has(Object)} and {@link #getRemainingMillis(Object)}.
     * </p>
     */
    public void cleanExpired() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

}