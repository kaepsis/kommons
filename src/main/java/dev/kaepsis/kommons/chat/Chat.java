package dev.kaepsis.kommons.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Chat {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})", Pattern.CASE_INSENSITIVE);
    private static final char COLOR_CHAR = '&';
    private static final Map<String, String> HEX_COLOR_CACHE = new ConcurrentHashMap<>();

    public static String color(String message) {
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

    public static String stripColors(String message) {
        String safe = nullToEmpty(message);
        if (safe.isEmpty()) return "";
        String noHex = HEX_PATTERN.matcher(safe).replaceAll("");
        return noHex.replace(String.valueOf(COLOR_CHAR), "");
    }

    public static void send(CommandSender receiver, String message, Object... placeholders) {
        if (receiver == null) return;
        String processed = nullToEmpty(message);
        if (processed.isEmpty()) return;
        if (placeholders != null && placeholders.length > 0) {
            processed = format(processed, placeholders);
        }
        receiver.sendMessage(color(processed));
    }

    public static String format(String message, Object... placeholders) {
        if (message == null) return "";
        if (placeholders == null || placeholders.length == 0) return message;
        String result = message;
        int len = placeholders.length & ~1;
        for (int i = 0; i < len; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return result;
    }

    public static void broadcast(String message, String permission, Object... placeholders) {
        String processed = nullToEmpty(message);
        if (processed.isEmpty()) return;
        if (placeholders != null && placeholders.length > 0) {
            processed = format(processed, placeholders);
        }
        final String colored = color(processed);
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> permission == null || player.hasPermission(permission))
                .forEach(player -> player.sendMessage(colored));
    }

    public static List<String> formatList(List<String> list, Object... placeholders) {
        if (list == null) return List.of();
        return list.stream()
                .map(line -> format(line, placeholders))
                .collect(Collectors.toList());
    }

    public static List<String> colorList(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .map(Chat::color)
                .collect(Collectors.toList());
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(Chat.color(title), Chat.color(subtitle), fadeIn, stay, fadeOut);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}
