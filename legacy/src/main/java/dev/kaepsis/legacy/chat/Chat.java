package dev.kaepsis.legacy.chat;

import com.google.common.collect.Lists;
import dev.kaepsis.kommons.chat.ColorStrategy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class Chat {

    private static final ColorStrategy STRATEGY = new LegacyColorStrategy();

    private Chat() {}

    public static String color(String message) {
        return STRATEGY.color(message);
    }

    public static String stripColors(String message) {
        return STRATEGY.stripColors(message);
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
        if (list == null) return Lists.newArrayList();
        return list.stream()
                .map(line -> format(line, placeholders))
                .collect(Collectors.toList());
    }

    public static List<String> colorList(List<String> list) {
        if (list == null) return Lists.newArrayList();
        return list.stream()
                .map(Chat::color)
                .collect(Collectors.toList());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

}
