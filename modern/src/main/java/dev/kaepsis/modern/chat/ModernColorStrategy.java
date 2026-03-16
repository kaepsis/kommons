package dev.kaepsis.modern.chat;

import dev.kaepsis.kommons.chat.ColorStrategy;
import org.bukkit.ChatColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModernColorStrategy implements ColorStrategy {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})", Pattern.CASE_INSENSITIVE);
    private static final char COLOR_CHAR = '&';
    private static final Map<String, String> HEX_COLOR_CACHE = new ConcurrentHashMap<>();

    @Override
    public String color(String message) {
        String safe = nullToEmpty(message);
        if (safe.isEmpty()) return "";

        Matcher matcher = HEX_PATTERN.matcher(safe);
        StringBuilder sb = new StringBuilder(safe.length() + 32);
        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = HEX_COLOR_CACHE.computeIfAbsent(hex, key -> {
                StringBuilder builder = new StringBuilder(14);
                builder.append(COLOR_CHAR).append('x');
                for (char c : key.toCharArray()) {
                    builder.append(COLOR_CHAR).append(c);
                }
                return builder.toString();
            });
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes(COLOR_CHAR, sb.toString());
    }

    @Override
    public String stripColors(String message) {
        String safe = nullToEmpty(message);
        if (safe.isEmpty()) return "";
        String noHex = HEX_PATTERN.matcher(safe).replaceAll("");
        return noHex.replace(String.valueOf(COLOR_CHAR), "");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}
