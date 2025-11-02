package com.github.beemerwt.essence.core.util;

import java.time.Duration;
import java.time.Instant;

public final class TimeFormats {
    public static String formatDurationFromNow(Instant instant) {
        Instant now = Instant.now();
        boolean future = instant.isAfter(now);

        Instant start = future ? now : instant;
        Instant end = future ? instant : now;

        Duration duration = Duration.between(start, end);

        long totalSeconds = duration.getSeconds();

        long years = totalSeconds / (365 * 24 * 3600);
        totalSeconds %= (365 * 24 * 3600);
        long days = totalSeconds / (24 * 3600);
        totalSeconds %= (24 * 3600);
        long hours = totalSeconds / 3600;
        totalSeconds %= 3600;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(" Year").append(years == 1 ? "" : "s");
        if (days > 0) append(sb, days, "Day");
        if (hours > 0) append(sb, hours, "Hour");
        if (minutes > 0 && sb.isEmpty()) append(sb, minutes, "Minute");
        if (seconds > 0 && sb.isEmpty()) append(sb, seconds, "Second");

        if (sb.isEmpty()) sb.append("Just now");
        else if (future) sb.append(" from now");
        else sb.append(" ago");

        return sb.toString();
    }

    private static void append(StringBuilder sb, long value, String unit) {
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(value).append(" ").append(unit);
        if (value != 1) sb.append("s");
    }

    private TimeFormats() {}
}
