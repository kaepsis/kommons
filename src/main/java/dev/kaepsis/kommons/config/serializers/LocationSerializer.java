package dev.kaepsis.kommons.config.serializers;

import dev.kaepsis.kommons.config.store.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Utility class for serializing and deserializing Bukkit {@link Location} objects
 * into and from a {@link DataStore} using nested keys.
 * <p>
 * A {@code Location} is stored as a set of individual properties under a common
 * key prefix. The stored fields are:
 * <ul>
 *   <li>{@code x} (double)</li>
 *   <li>{@code y} (double)</li>
 *   <li>{@code z} (double)</li>
 *   <li>{@code yaw} (float)</li>
 *   <li>{@code pitch} (float)</li>
 *   <li>{@code world} (string – world name)</li>
 * </ul>
 * </p>
 * <p>
 * This serialization format is compatible with YAML, JSON, or any configuration
 * backend that supports dotted key paths or nested maps.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * DataStore store = ...;
 * Location spawn = player.getLocation();
 * LocationSerializer.serialize(store, "spawn", spawn);
 *
 * Location loaded = LocationSerializer.deserialize(store, "spawn");
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public class LocationSerializer {

    /**
     * Serializes a {@link Location} into a {@link DataStore} under the given key prefix.
     * <p>
     * The location's world must not be {@code null} (i.e., the location must belong
     * to a valid world). The following keys are written:
     * {@code key.x}, {@code key.y}, {@code key.z}, {@code key.yaw}, {@code key.pitch},
     * and {@code key.world}.
     * </p>
     *
     * @param store    the data store to write into (must not be {@code null})
     * @param key      the base key prefix (e.g., {@code "home"} or {@code "spawn"})
     * @param location the location to serialize (must not be {@code null} and must have a non‑null world)
     * @throws NullPointerException if {@code location.getWorld()} is {@code null}
     */
    public static void serialize(DataStore store, String key, Location location) {
        store.set(key + ".x", location.getX());
        store.set(key + ".y", location.getY());
        store.set(key + ".z", location.getZ());
        store.set(key + ".yaw", location.getYaw());
        store.set(key + ".pitch", location.getPitch());
        store.set(key + ".world", Objects.requireNonNull(location.getWorld()).getName());
    }

    /**
     * Deserializes a {@link Location} from a {@link DataStore} using the given key prefix.
     * <p>
     * The method reads the nested keys {@code key.x}, {@code key.y}, {@code key.z},
     * {@code key.yaw}, {@code key.pitch}, and {@code key.world}. Missing numeric fields
     * default to {@code 0}. If the world name is missing or the world is not loaded,
     * {@code null} is used as the world, which may cause the returned location to be invalid.
     * </p>
     *
     * @param store the data store to read from (must not be {@code null})
     * @param key   the base key prefix under which the location was stored
     * @return the reconstructed location (the world may be {@code null} if not found)
     */
    public static Location deserialize(DataStore store, String key) {
        String base = key + ".";
        double x = store.getDouble(base + "x", 0L);
        double y = store.getDouble(base + "y", 0L);
        double z = store.getDouble(base + "z", 0L);
        float yaw = store.getFloat(base + "yaw", 0L);
        float pitch = store.getFloat(base + "pitch", 0L);
        String world = store.getString(base + "world");
        World worldObj = Bukkit.getWorld(world);
        return new Location(worldObj, x, y, z, yaw, pitch);
    }

}