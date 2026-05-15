package dev.kaepsis.kommons.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * A utility class for time‑related operations, including duration formatting,
 * parsing of human‑readable duration strings, and timestamp formatting.
 * <p>
 * This class provides methods to convert milliseconds into human‑readable strings
 * (e.g., {@code "2h 30m 5s"}), parse duration strings like {@code "5m"}, {@code "2h"},
 * {@code "7d"} or {@code "1w"} (weeks) into {@link Duration} objects, and format
 * timestamps for display. It also includes a Minecraft‑specific method to compute
 * an absolute timestamp from a relative duration and a reference instant.
 * </p>
 * <p>
 * All methods are static, and the class cannot be instantiated.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * String formatted = Time.formatDuration(125000);      // "2m 5s"
 * Duration duration = Time.parseDuration("3h");       // PT3H
 * long expiry = Time.parseMinecraftDuration(Instant.now(), "15m");
 * String timestamp = Time.formatTimestamp(expiry);    // "2026-05-15 14:30:00"
 * }</pre>
 * </p>
 *
 * @author Kaepsis
 * @version 260515
 * @since 260514
 */
public final class Time {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);

    private Time() {
    }

    /**
     * Formats a duration given in milliseconds into a human‑readable string.
     * <p>
     * The output format uses {@code h} for hours, {@code m} for minutes, and {@code s}
     * for seconds. Units with a value of zero are omitted. If the duration is zero,
     * the string {@code "0s"} is returned.
     * </p>
     *
     * @param millis the duration in milliseconds (must be non‑negative)
     * @return a formatted string like {@code "2h 30m 5s"} or {@code "45s"}
     * @throws IllegalArgumentException if {@code millis} is negative
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("La durata non può essere negativa: " + millis);
        }
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder(16);
        return getString(seconds, hours, minutes, sb);
    }

    private static String getString(long seconds, long hours, long minutes, StringBuilder sb) {
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Parses a duration string into a {@link Duration} object.
     * <p>
     * The string must consist of a non‑negative integer number immediately followed
     * by a unit character. Supported units are:
     * <ul>
     *   <li>{@code s} – seconds</li>
     *   <li>{@code m} – minutes</li>
     *   <li>{@code h} – hours</li>
     *   <li>{@code d} – days</li>
     *   <li>{@code w} – weeks (converted to 7 days)</li>
     * </ul>
     * Examples: {@code "30s"}, {@code "5m"}, {@code "2h"}, {@code "1d"}, {@code "2w"}.
     * </p>
     *
     * @param input the duration string (e.g., {@code "10m"}), must not be {@code null} or empty
     * @return the corresponding {@code Duration}
     * @throws IllegalArgumentException if the input is {@code null}, empty, malformed,
     *                                  or contains a negative number or an unknown unit
     */
    public static Duration parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input nullo o vuoto");
        }
        input = input.trim().toLowerCase(Locale.ROOT);
        if (input.length() < 2) {
            throw new IllegalArgumentException("Formato troppo corto: " + input);
        }
        char unit = input.charAt(input.length() - 1);
        long amount = getAmount(input);
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'w' -> Duration.ofDays(amount * 7);
            default -> throw new IllegalArgumentException("Unità sconosciuta: '" + unit + "'");
        };
    }

    private static long getAmount(String input) {
        String numberPart = input.substring(0, input.length() - 1).trim();
        if (numberPart.isEmpty()) {
            throw new IllegalArgumentException("Manca la parte numerica in: " + input);
        }

        long amount;
        try {
            amount = Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Numero non valido: " + numberPart, e);
        }

        if (amount < 0) {
            throw new IllegalArgumentException("La quantità non può essere negativa: " + amount);
        }
        return amount;
    }

    /**
     * Converts a relative duration string (e.g., {@code "10m"}) into an absolute
     * timestamp in milliseconds, starting from the given reference instant.
     * <p>
     * This is useful for Minecraft commands that accept relative time expressions
     * like {@code /time set 2h}. The method parses the duration using
     * {@link #parseDuration(String)} and adds it to the provided {@code now} instant.
     * </p>
     *
     * @param now   the reference instant (typically the current server time)
     * @param input the duration string (e.g., {@code "5m"}, {@code "2d"})
     * @return the absolute timestamp in milliseconds (epoch millis)
     * @throws IllegalArgumentException if the input is invalid (see {@link #parseDuration})
     */
    public static long parseMinecraftDuration(Instant now, String input) {
        Duration duration = parseDuration(input);
        return now.plus(duration).toEpochMilli();
    }

    /**
     * Formats a timestamp (milliseconds since epoch) into a readable date‑time string.
     * <p>
     * The format is {@code yyyy-MM-dd HH:mm:ss} using the system's default time zone.
     * </p>
     *
     * @param millis the timestamp in milliseconds
     * @return a formatted string, e.g., {@code "2026-05-15 14:30:00"}
     */
    public static String formatTimestamp(long millis) {
        LocalDateTime localDateTime = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(DATE_FORMATTER);
    }

    /**
     * Converts a duration in milliseconds into a Minecraft‑style string.
     * <p>
     * The output includes days ({@code d}), hours ({@code h}), minutes ({@code m}),
     * and seconds ({@code s}), omitting zero values. Unlike {@link #formatDuration},
     * this method includes days and never adds seconds if the duration is zero?
     * (Actually, for zero it adds {@code "0s"} because of the same logic.)
     * </p>
     * <p>
     * Example: {@code 172800000} ms → {@code "2d"}.
     * </p>
     *
     * @param millis the duration in milliseconds (must be non‑negative)
     * @return a formatted string like {@code "1d 3h 5m 10s"} or {@code "0s"} for zero
     * @throws IllegalArgumentException if {@code millis} is negative
     */
    public static String toMinecraftString(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("La durata non può essere negativa: " + millis);
        }
        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        return getString(seconds, hours, minutes, sb);
    }
}