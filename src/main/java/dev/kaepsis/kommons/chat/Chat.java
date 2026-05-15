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

/**
 * A utility class for handling chat messages, colors, placeholders, and broadcasting.
 * <p>
 * This class provides methods to translate color codes (including hex colors using the {@code &#RRGGBB} format),
 * strip colors, replace placeholders, send messages to players/command senders, broadcast with permission checks,
 * and work with lists of formatted or colored strings.
 * </p>
 * <p>
 * All methods are static and handle {@code null} inputs gracefully, returning empty strings or empty lists where appropriate.
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 */
public class Chat {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})", Pattern.CASE_INSENSITIVE);
    private static final char COLOR_CHAR = '&';
    private static final Map<String, String> HEX_COLOR_CACHE = new ConcurrentHashMap<>();

    /**
     * Translates color codes in the given message into Minecraft chat colors.
     * <p>
     * Supports standard {@code &} color codes (e.g., {@code &a}, {@code &l}) and hex colors using the format
     * {@code &#RRGGBB} (e.g., {@code &#FF5555}). Hex colors are cached for performance.
     * </p>
     *
     * @param message the raw message containing color codes; may be {@code null}
     * @return the colored message, or an empty string if the input is {@code null} or empty
     */
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

    /**
     * Removes all color codes from the given message.
     * <p>
     * This includes both standard {@code &} color codes and hex color codes (e.g., {@code &#RRGGBB}).
     * </p>
     *
     * @param message the colored message; may be {@code null}
     * @return the uncolored message, or an empty string if the input is {@code null} or empty
     */
    public static String stripColors(String message) {
        String safe = nullToEmpty(message);
        if (safe.isEmpty()) return "";
        String noHex = HEX_PATTERN.matcher(safe).replaceAll("");
        return noHex.replace(String.valueOf(COLOR_CHAR), "");
    }

    /**
     * Sends a formatted and colored message to a {@link CommandSender}.
     * <p>
     * Placeholders are replaced using {@link #format(String, Object...)} before color translation.
     * If the receiver or message is {@code null}/empty, the method does nothing.
     * </p>
     *
     * @param receiver      the recipient of the message; may be {@code null}
     * @param message       the message containing optional placeholders and color codes
     * @param placeholders  key‑value pairs of placeholders (e.g., {@code "{name}", "John"}); must be an even number of arguments
     */
    public static void send(CommandSender receiver, String message, Object... placeholders) {
        if (receiver == null) return;
        String processed = nullToEmpty(message);
        if (processed.isEmpty()) return;
        if (placeholders != null && placeholders.length > 0) {
            processed = format(processed, placeholders);
        }
        receiver.sendMessage(color(processed));
    }

    /**
     * Replaces placeholders in a message with the given values.
     * <p>
     * Placeholders are provided as alternating key‑value pairs: {@code key1, value1, key2, value2, ...}.
     * If the number of arguments is odd, the last unmatched argument is ignored.
     * </p>
     *
     * @param message       the message containing placeholder keys (e.g., {@code "{name}"}); may be {@code null}
     * @param placeholders  key‑value pairs of placeholders; may be {@code null} or empty
     * @return the formatted message, or an empty string if the message is {@code null}
     */
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

    /**
     * Broadcasts a colored message to all online players who have the specified permission.
     * <p>
     * The message is processed through {@link #format(String, Object...)} if placeholders are provided,
     * then colored with {@link #color(String)}. If the permission is {@code null}, all online players receive the message.
     * </p>
     *
     * @param message      the message to broadcast; if empty or {@code null}, nothing is broadcast
     * @param permission   the required permission node; may be {@code null} to allow all players
     * @param placeholders key‑value pairs of placeholders (e.g., {@code "{server}", "Hub"})
     */
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

    /**
     * Applies {@link #format(String, Object...)} to each string in the given list.
     * <p>
     * Returns a new list; the original list is not modified.
     * </p>
     *
     * @param list          the list of strings to format; may be {@code null}
     * @param placeholders  key‑value pairs of placeholders
     * @return a new list with placeholders replaced in each element, or an empty list if the input list is {@code null}
     */
    public static List<String> formatList(List<String> list, Object... placeholders) {
        if (list == null) return List.of();
        return list.stream()
                .map(line -> format(line, placeholders))
                .collect(Collectors.toList());
    }

    /**
     * Applies {@link #color(String)} to each string in the given list.
     * <p>
     * Returns a new list; the original list is not modified.
     * </p>
     *
     * @param list the list of raw strings containing color codes; may be {@code null}
     * @return a new list with colors translated, or an empty list if the input list is {@code null}
     */
    public static List<String> colorList(List<String> list) {
        if (list == null) return List.of();
        return list.stream()
                .map(Chat::color)
                .collect(Collectors.toList());
    }

    /**
     * Sends a title and subtitle to a player.
     * <p>
     * Both title and subtitle are processed with {@link #color(String)} before being sent.
     * </p>
     *
     * @param player    the player to receive the title; must not be {@code null}
     * @param title     the title text; may contain color codes
     * @param subtitle  the subtitle text; may contain color codes
     * @param fadeIn    ticks for fade‑in effect
     * @param stay      ticks the title stays visible
     * @param fadeOut   ticks for fade‑out effect
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(Chat.color(title), Chat.color(subtitle), fadeIn, stay, fadeOut);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}