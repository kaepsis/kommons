package dev.kaepsis.kommons.cooldown.abstraction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public abstract class CooldownManager<K> {

    protected final ConcurrentHashMap<K, Long> cooldowns = new ConcurrentHashMap<>();

    public void set(K key, long duration, TimeUnit unit) {
        long expiry = System.currentTimeMillis() + unit.toMillis(duration);
        cooldowns.put(key, expiry);
    }

    public boolean has(K key) {
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;
        if (expiry > System.currentTimeMillis()) return true;
        cooldowns.remove(key);
        return false;
    }

    public long getRemainingMillis(K key) {
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining > 0) return remaining;
        cooldowns.remove(key);
        return 0;
    }

    public void remove(K key) {
        cooldowns.remove(key);
    }

    public void cleanExpired() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

}
