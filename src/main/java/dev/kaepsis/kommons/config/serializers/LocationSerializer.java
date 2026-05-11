package dev.kaepsis.kommons.config.serializers;

import dev.kaepsis.kommons.config.store.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public class LocationSerializer {

    public static void serialize(DataStore store, String key, Location location) {
        store.set(key + ".x", location.getX());
        store.set(key + ".y", location.getY());
        store.set(key + ".z", location.getZ());
        store.set(key + ".yaw", location.getYaw());
        store.set(key + ".pitch", location.getPitch());
        store.set(key + ".world", Objects.requireNonNull(location.getWorld()).getName());
    }

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
