package dev.kaepsis.legacy.chat;

import dev.kaepsis.kommons.chat.ColorStrategy;
import org.bukkit.ChatColor;

public final class LegacyColorStrategy implements ColorStrategy {

    private static final char COLOR_CHAR = '&';

    @Override
    public String color(String message) {
        return ChatColor.translateAlternateColorCodes(COLOR_CHAR, nullToEmpty(message));
    }

    @Override
    public String stripColors(String message) {
        return nullToEmpty(message).replace(String.valueOf(COLOR_CHAR), "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}