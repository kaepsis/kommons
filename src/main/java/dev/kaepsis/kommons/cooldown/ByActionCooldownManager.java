package dev.kaepsis.kommons.cooldown;

import dev.kaepsis.kommons.cooldown.abstraction.CooldownManager;
import dev.kaepsis.kommons.cooldown.objects.ActionKey;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ByActionCooldownManager extends CooldownManager<ActionKey> {

    public void set(UUID uuid, String action, long duration, TimeUnit unit) {
        super.set(new ActionKey(uuid, action), duration, unit);
    }

    public boolean has(UUID uuid, String action) {
        return super.has(new ActionKey(uuid, action));
    }

    public long getRemainingMillis(UUID uuid, String action) {
        return super.getRemainingMillis(new ActionKey(uuid, action));
    }

    public void remove(UUID uuid, String action) {
        super.remove(new ActionKey(uuid, action));
    }

    public void removeAll(UUID uuid) {
        cooldowns.keySet().removeIf(key -> key.uuid().equals(uuid));
    }

}
