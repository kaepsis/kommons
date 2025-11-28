package com.github.kaepsis;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class Time {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder(16);
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString();
    }

    public static long parseMinecraftDuration(Instant now, String input) {
        if (input == null || input.length() < 2) return -1L;
        char lastChar = input.charAt(input.length() - 1);
        long temporalAmount = Long.parseLong(input.substring(0, input.length() - 1));
        return switch (lastChar) {
            case 's' -> now.plusSeconds(temporalAmount).toEpochMilli();
            case 'm' -> now.plusSeconds(temporalAmount * 60).toEpochMilli();
            case 'h' -> now.plusSeconds(temporalAmount * 3600).toEpochMilli();
            case 'd' -> now.plusSeconds(temporalAmount * 86400).toEpochMilli();
            default -> -1L;
        };
    }

    public static String formatTimestamp(long millis) {
        LocalDateTime localDateTime = Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return localDateTime.format(DATE_FORMATTER);
    }
}
