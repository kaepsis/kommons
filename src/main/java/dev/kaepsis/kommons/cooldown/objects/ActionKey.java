package dev.kaepsis.kommons.cooldown.objects;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public record ActionKey(UUID uuid, String action) {
    public ActionKey(UUID uuid, String action) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActionKey that = (ActionKey) o;
        return uuid.equals(that.uuid) && action.equals(that.action);
    }

    @NotNull
    @Override
    public String toString() {
        return "ActionKey{" + "uuid=" + uuid + ", action='" + action + '\'' + '}';
    }
}