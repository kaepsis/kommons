package dev.kaepsis.kommons.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * A utility class for working with Bukkit {@link Location} objects.
 * <p>
 * This class provides a set of static helper methods for common location operations
 * such as serialization/deserialization to/from string, distance calculations,
 * chunk management, and location normalisation. All methods are designed to be
 * null‑safe where appropriate, throwing {@link NullPointerException} or
 * {@link IllegalArgumentException} when required arguments are missing or invalid.
 * </p>
 * <p>
 * The serialization format is:
 * <pre>{@code world;x;y;z[;yaw[;pitch]]}</pre>
 * For example: {@code "world;100.5;64.0;-200.0;45.0;0.0"}.
 * Yaw and pitch are optional; if omitted they default to {@code 0.0f}.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * Location spawn = new Location(world, 0, 64, 0);
 * String serialized = LocationUtils.serialize(spawn);
 * Location deserialized = LocationUtils.deserialize(serialized);
 *
 * double dist = LocationUtils.distance(loc1, loc2);
 * if (!LocationUtils.isChunkLoaded(loc)) {
 *     LocationUtils.loadChunk(loc);
 * }
 * Location center = LocationUtils.center(blockLoc);
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 1.0.0
 * @since 1.0.0
 */
public final class LocationUtils {

    /**
     * Serializes a {@link Location} into a compact string representation.
     * <p>
     * The format is: {@code worldName;x;y;z[;yaw[;pitch]]}. Yaw and pitch are included
     * only if they are non‑zero. The world of the location must not be {@code null}.
     * </p>
     *
     * @param location the location to serialize (must not be {@code null}, world must exist)
     * @return a string representation of the location
     * @throws NullPointerException     if {@code location} is {@code null}
     * @throws IllegalArgumentException if {@code location.getWorld()} is {@code null}
     */
    public static String serialize(Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = location.getWorld();
        if (world == null) throw new IllegalArgumentException("Location world is null");
        String base = world.getName() + ";" + location.getX() + ";" + location.getY() + ";" + location.getZ();
        if (location.getYaw() != 0.0f || location.getPitch() != 0.0f) {
            return base + ";" + location.getYaw() + ";" + location.getPitch();
        }
        return base;
    }

    /**
     * Deserializes a string previously created by {@link #serialize(Location)} back into a {@link Location}.
     * <p>
     * The string must be in the format {@code world;x;y;z} or {@code world;x;y;z;yaw} or
     * {@code world;x;y;z;yaw;pitch}. The world must be currently loaded on the Bukkit server,
     * otherwise an exception is thrown. Missing yaw/pitch default to {@code 0.0f}.
     * </p>
     *
     * @param location the serialized location string (must not be {@code null})
     * @return the reconstructed location
     * @throws NullPointerException     if {@code location} is {@code null}
     * @throws IllegalArgumentException if the string format is invalid or the world is not found
     */
    public static Location deserialize(String location) {
        Objects.requireNonNull(location, "location string cannot be null");
        String[] parts = location.split(";");
        if (parts.length < 4 || parts.length > 6) {
            throw new IllegalArgumentException("Invalid location string: " + location);
        }
        String worldName = parts[0].trim();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("world not found: " + worldName);
        }
        double x = Double.parseDouble(parts[1].trim());
        double y = Double.parseDouble(parts[2].trim());
        double z = Double.parseDouble(parts[3].trim());
        float yaw = 0.0f;
        float pitch = 0.0f;

        if (parts.length >= 5) {
            yaw = Float.parseFloat(parts[4].trim());
        }
        if (parts.length == 6) {
            pitch = Float.parseFloat(parts[5].trim());
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Returns the squared distance between two locations.
     * <p>
     * If the locations are in different worlds, this method returns {@link Double#MAX_VALUE}
     * to indicate that they are essentially infinitely far apart.
     * </p>
     *
     * @param loc1 the first location (must not be {@code null})
     * @param loc2 the second location (must not be {@code null})
     * @return the squared Euclidean distance, or {@code Double.MAX_VALUE} if worlds differ
     * @throws NullPointerException if either location is {@code null}
     */
    public static double distanceSquared(Location loc1, Location loc2) {
        if (!Objects.equals(loc1.getWorld(), loc2.getWorld())) return Double.MAX_VALUE;
        double dx = loc1.getX() - loc2.getX();
        double dy = loc1.getY() - loc2.getY();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Returns the Euclidean distance between two locations.
     * <p>
     * If the locations are in different worlds, this method returns {@link Double#MAX_VALUE}.
     * </p>
     *
     * @param loc1 the first location (must not be {@code null})
     * @param loc2 the second location (must not be {@code null})
     * @return the distance, or {@code Double.MAX_VALUE} if worlds differ
     * @throws NullPointerException if either location is {@code null}
     */
    public static double distance(Location loc1, Location loc2) {
        return Math.sqrt(distanceSquared(loc1, loc2));
    }

    /**
     * Checks whether the chunk containing the given location is loaded.
     *
     * @param loc the location (must have a non‑null world)
     * @return {@code true} if the chunk is loaded, {@code false} otherwise (also if world is {@code null})
     */
    public static boolean isChunkLoaded(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    /**
     * Loads the chunk containing the given location.
     * <p>
     * This method attempts to load the chunk synchronously. The boolean parameter
     * {@code true} passed to {@link World#loadChunk(int, int, boolean)} indicates that
     * chunk generation should be attempted if the chunk does not exist.
     * </p>
     *
     * @param loc the location (must have a non‑null world)
     * @return {@code true} if the chunk was loaded (or already loaded), {@code false} otherwise
     */
    public static boolean loadChunk(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        return world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true);
    }

    /**
     * Returns the center of the block at the given location.
     * <p>
     * The center of a block is at its integer coordinates plus {@code 0.5} in each axis.
     * This method clones the original location and adds the offset.
     * </p>
     *
     * @param loc the location (typically a block location; must not be {@code null})
     * @return a new location at the centre of the block
     */
    public static Location center(Location loc) {
        return loc.clone().add(0.5, 0.5, 0.5);
    }

    /**
     * Returns a new location snapped to the block coordinates of the given location.
     * <p>
     * The resulting location has the same world, but its X, Y, Z are set to the
     * integer block coordinates (floor). Yaw and pitch are reset to {@code 0}.
     * </p>
     *
     * @param loc the original location (must not be {@code null})
     * @return a new location at the block position
     */
    public static Location blockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Checks whether the given location is valid (non‑null and has a non‑null world).
     *
     * @param loc the location to check (may be {@code null})
     * @return {@code true} if the location is not {@code null} and its world is not {@code null}
     */
    public static boolean isValid(Location loc) {
        return loc != null && loc.getWorld() != null;
    }

}