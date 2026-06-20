package dev.kaepsis.kommons.cooldown.objects;

import dev.kaepsis.kommons.cooldown.abstraction.CooldownManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * A composite key representing a cooldown for a specific player and action.
 * <p>
 * This record is intended to be used with {@link CooldownManager}
 * to track per‑player, per‑action cooldowns independently. For example, a player might have separate
 * cooldowns for {@code "command.heal"} and {@code "command.fly"}.
 * </p>
 * <p>
 * Both the {@code uuid} and {@code action} fields are required and cannot be {@code null}.
 * The record provides automatic implementations of {@link #equals(Object)}, {@link #hashCode()},
 * and {@link #toString()}.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * CooldownManager<ActionKey> manager = new CooldownManager<>() {};
 * UUID playerId = player.getUniqueId();
 * ActionKey key = new ActionKey(playerId, "ability.dash");
 * manager.set(key, 5, TimeUnit.SECONDS);
 *
 * if (!manager.has(new ActionKey(playerId, "ability.dash"))) {
 *     // player can dash again
 * }
 * }</pre>
 * </p>
 *
 * @param uuid   the unique identifier of the player (never {@code null})
 * @param action the action name or identifier (never {@code null})
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public record ActionKey(UUID uuid, String action) {

    /**
     * Constructs an {@code ActionKey} with the specified UUID and action.
     *
     * @param uuid   the player's UUID (must not be {@code null})
     * @param action the action identifier (must not be {@code null})
     * @throws NullPointerException if either argument is {@code null}
     */
    public ActionKey(UUID uuid, String action) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
    }

    /**
     * Indicates whether some other object is equal to this one.
     * <p>
     * Two {@code ActionKey} instances are considered equal if they have the same
     * {@code uuid} and the same {@code action}.
     * </p>
     *
     * @param o the reference object with which to compare
     * @return {@code true} if this object is the same as the {@code o} argument;
     *         {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionKey that = (ActionKey) o;
        return uuid.equals(that.uuid) && action.equals(that.action);
    }

    /**
     * Returns a string representation of this {@code ActionKey}.
     * <p>
     * The format is {@code ActionKey{uuid=..., action='...'}}.
     * </p>
     *
     * @return a human‑readable string representation
     */
    @NotNull
    @Override
    public String toString() {
        return "ActionKey{" + "uuid=" + uuid + ", action='" + action + '\'' + '}';
    }
}