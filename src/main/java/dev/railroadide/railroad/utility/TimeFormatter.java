package dev.railroadide.railroad.utility;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class TimeFormatter {
    public static String formatElapsed(long epochMillis) {
        if (epochMillis == -1)
            return "never";

        Instant then = Instant.ofEpochMilli(epochMillis);
        Instant now = Instant.now();
        if (then.isAfter(now))
            return "in the future";

        // Handle high-level Calendar units (Years/Months)
        ZonedDateTime thenZoned = then.atZone(ZoneId.systemDefault());
        ZonedDateTime nowZoned = now.atZone(ZoneId.systemDefault());

        long years = ChronoUnit.YEARS.between(thenZoned, nowZoned);
        if (years > 0) {
            // Check if we should round up (e.g., 1 year 7 months -> 2 years)
            long monthsInYear = ChronoUnit.MONTHS.between(thenZoned, nowZoned) % 12;
            if (monthsInYear >= 6) {
                years++;
            }

            return years > 10
                ? "more than 10 years ago"
                : pluralize(years, "year");
        }

        long months = ChronoUnit.MONTHS.between(thenZoned, nowZoned);
        if (months > 0)
            return pluralize(months, "month");

        // Handle precise Duration units (Weeks/Days/Hours/etc)
        Duration diff = Duration.between(then, now);

        long days = diff.toDays();
        if (days >= 7) {
            long weeks = days / 7;
            if (days % 7 >= 4) {
                weeks++; // Round up
            }

            return pluralize(weeks, "week");
        }

        if (days > 0)
            return pluralize(days, "day");

        long hours = diff.toHours();
        if (hours > 0)
            return pluralize(hours, "hour");

        long minutes = diff.toMinutes();
        if (minutes > 0)
            return pluralize(minutes, "minute");

        long seconds = diff.getSeconds();
        return seconds < 5 ? "just now" : pluralize(seconds, "second");
    }

    private static String pluralize(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? " ago" : "s ago");
    }

    public static String formatDateTime(long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
        return zdt.toLocalDateTime().toString().replace('T', ' ');
    }
}
