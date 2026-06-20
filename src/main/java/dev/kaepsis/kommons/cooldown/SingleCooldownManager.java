package dev.kaepsis.kommons.cooldown;

import dev.kaepsis.kommons.cooldown.abstraction.CooldownManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A simple cooldown manager that tracks a single cooldown per player (UUID).
 * <p>
 * This class extends {@link CooldownManager} with a key type of {@link UUID},
 * providing convenience methods that operate directly on player UUIDs.
 * It is suitable for scenarios where each player has at most one active cooldown
 * (e.g., a global command cooldown or a “use any ability” cooldown).
 * </p>
 * <p>
 * All methods delegate to the superclass, and the type parameter makes the API
 * more natural when working with player‑specific timers.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * SingleCooldownManager manager = new SingleCooldownManager();
 * manager.set(playerId, 10, TimeUnit.SECONDS);
 *
 * if (!manager.has(playerId)) {
 *     // player can execute the action again
 * }
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public class SingleCooldownManager extends CooldownManager<UUID> {

    /**
     * Sets a cooldown for the given player.
     *
     * @param uuid     the player's UUID
     * @param duration the length of the cooldown
     * @param unit     the time unit of the duration
     */
    public void set(UUID uuid, long duration, TimeUnit unit) {
        super.set(uuid, duration, unit);
    }

    /**
     * Checks whether the given player is currently on cooldown.
     *
     * @param uuid the player's UUID
     * @return {@code true} if the cooldown is active, {@code false} otherwise
     */
    public boolean has(UUID uuid) {
        return super.has(uuid);
    }

    /**
     * Returns the remaining cooldown time for the given player, in milliseconds.
     *
     * @param uuid the player's UUID
     * @return the remaining milliseconds, or {@code 0} if no active cooldown exists
     */
    public long getRemainingMillis(UUID uuid) {
        return super.getRemainingMillis(uuid);
    }

    /**
     * Removes the cooldown for the given player, if present.
     *
     * @param uuid the player's UUID
     */
    public void remove(UUID uuid) {
        super.remove(uuid);
    }

}