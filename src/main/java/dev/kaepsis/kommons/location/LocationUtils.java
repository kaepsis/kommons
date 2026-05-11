package dev.kaepsis.kommons.location;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public final class LocationUtils {

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

    public static double distanceSquared(Location loc1, Location loc2) {
        if (!Objects.equals(loc1.getWorld(), loc2.getWorld())) return Double.MAX_VALUE;
        double dx = loc1.getX() - loc2.getX();
        double dy = loc1.getY() - loc2.getY();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distance(Location loc1, Location loc2) {
        return Math.sqrt(distanceSquared(loc1, loc2));
    }

    public static boolean isChunkLoaded(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        return world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static boolean loadChunk(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        return world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true);
    }

    public static Location center(Location loc) {
        return loc.clone().add(0.5, 0.5, 0.5);
    }

    public static Location blockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static boolean isValid(Location loc) {
        return loc != null && loc.getWorld() != null;
    }

}
