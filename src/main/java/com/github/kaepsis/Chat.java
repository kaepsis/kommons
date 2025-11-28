package com.github.kaepsis;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Chat {

    private static final String REGEX = "&#([A-Fa-f0-9]{6})";

    private static final Pattern HEX_PATTERN = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
    private static final char COLOR_CHAR = '&';
    private static final Map<String, String> HEX_COLOR_CACHE = new ConcurrentHashMap<>();

    public static String color(final String message) {
        if (message == null || message.isEmpty()) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            final String hex = matcher.group(1);
            final String replacement = HEX_COLOR_CACHE.computeIfAbsent(hex, key -> {
                final StringBuilder builder = new StringBuilder(message.length() + 32);
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

    public static String removeColors(String message) {
        if (message == null || message.isEmpty()) return "";
        message = message.replaceAll(REGEX, "");
        message = message.replaceAll(COLOR_CHAR + "", "");
        return message;
    }

    public static void send(CommandSender receiver, String message, Object... placeholders) {
        if (message == null || message.isEmpty()) return;
        String modifiedMessage = format(message, placeholders);
        send(receiver, modifiedMessage);
    }

    public static void send(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(color(message));
    }

    public static String format(String message, Object... placeholders) {
        String result = message;
        for (int i = 0; i < placeholders.length; i += 2) {
            String placeholder = String.valueOf(placeholders[i]);
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return result;
    }

    public static void broadcast(String message, @Nullable String permission, @Nullable Object... placeholders) {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> permission == null || player.hasPermission(permission))
                .forEach(player -> send(player, message, placeholders));
    }

    public static ArrayList<String> formatList(List<String> list, Object... placeholders) {
        return new ArrayList<>(
                list.stream()
                        .map(line -> format(line, placeholders))
                        .toList()
        );
    }

    public static ArrayList<String> colorList(List<String> list) {
        return new ArrayList<>(
                list.stream()
                        .map(Chat::color)
                        .toList()
        );
    }
}
