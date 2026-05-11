package dev.kaepsis.kommons.cooldown;

import dev.kaepsis.kommons.cooldown.abstraction.CooldownManager;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SingleCooldownManager extends CooldownManager<UUID> {

    public void set(UUID uuid, long duration, TimeUnit unit) {
        super.set(uuid, duration, unit);
    }

    public boolean has(UUID uuid) {
        return super.has(uuid);
    }

    public long getRemainingMillis(UUID uuid) {
        return super.getRemainingMillis(uuid);
    }

    public void remove(UUID uuid) {
        super.remove(uuid);
    }

}
