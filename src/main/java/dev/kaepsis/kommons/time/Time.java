package dev.kaepsis.kommons.time;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Time {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT);

    private Time() {
    }

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
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

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
        switch (unit) {
            case 's':
                return Duration.ofSeconds(amount);
            case 'm':
                return Duration.ofMinutes(amount);
            case 'h':
                return Duration.ofHours(amount);
            case 'd':
                return Duration.ofDays(amount);
            case 'w':
                return Duration.ofDays(amount * 7);
            default:
                throw new IllegalArgumentException("Unità sconosciuta: '" + unit + "'");
        }
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

    public static long parseMinecraftDuration(Instant now, String input) {
        Duration duration = parseDuration(input);
        return now.plus(duration).toEpochMilli();
    }

    public static String formatTimestamp(long millis) {
        LocalDateTime localDateTime = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(DATE_FORMATTER);
    }

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