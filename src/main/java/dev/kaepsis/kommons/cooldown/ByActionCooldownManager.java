package dev.kaepsis.kommons.cooldown;

import dev.kaepsis.kommons.cooldown.abstraction.CooldownManager;
import dev.kaepsis.kommons.cooldown.objects.ActionKey;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A cooldown manager that tracks cooldowns per player (UUID) and per action name.
 * <p>
 * This class extends {@link CooldownManager} with a key type of {@link ActionKey},
 * providing convenience methods that accept a {@code UUID} and an action {@code String}
 * instead of constructing {@code ActionKey} objects manually. It is ideal for scenarios
 * where multiple independent cooldowns are needed for the same player (e.g., different
 * commands or abilities).
 * </p>
 * <p>
 * In addition to the standard operations (set, has, getRemaining, remove), this manager
 * provides {@link #removeAll(UUID)} to clear all cooldowns associated with a specific
 * player.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * ByActionCooldownManager manager = new ByActionCooldownManager();
 * manager.set(playerId, "heal", 30, TimeUnit.SECONDS);
 * manager.set(playerId, "fly", 5, TimeUnit.MINUTES);
 *
 * if (!manager.has(playerId, "heal")) {
 *     // player can use /heal again
 * }
 *
 * // Clear all cooldowns for the player (e.g., on logout)
 * manager.removeAll(playerId);
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class ByActionCooldownManager extends CooldownManager<ActionKey> {

    /**
     * Sets a cooldown for a specific player and action.
     *
     * @param uuid     the player's UUID
     * @param action   the action identifier (e.g., {@code "command.heal"})
     * @param duration the length of the cooldown
     * @param unit     the time unit of the duration
     */
    public void set(UUID uuid, String action, long duration, TimeUnit unit) {
        super.set(new ActionKey(uuid, action), duration, unit);
    }

    /**
     * Checks whether the given player is on cooldown for the specified action.
     *
     * @param uuid   the player's UUID
     * @param action the action identifier
     * @return {@code true} if the cooldown is active, {@code false} otherwise
     */
    public boolean has(UUID uuid, String action) {
        return super.has(new ActionKey(uuid, action));
    }

    /**
     * Returns the remaining cooldown time for the given player and action, in milliseconds.
     *
     * @param uuid   the player's UUID
     * @param action the action identifier
     * @return the remaining milliseconds, or {@code 0} if no active cooldown exists
     */
    public long getRemainingMillis(UUID uuid, String action) {
        return super.getRemainingMillis(new ActionKey(uuid, action));
    }

    /**
     * Removes the cooldown for a specific player and action, if present.
     *
     * @param uuid   the player's UUID
     * @param action the action identifier
     */
    public void remove(UUID uuid, String action) {
        super.remove(new ActionKey(uuid, action));
    }

    /**
     * Removes all cooldowns associated with the given player, regardless of action.
     * <p>
     * This method iterates over the internal map and removes every entry whose
     * {@code ActionKey} contains the specified UUID.
     * </p>
     *
     * @param uuid the player's UUID
     */
    public void removeAll(UUID uuid) {
        cooldowns.keySet().removeIf(key -> key.uuid().equals(uuid));
    }

}