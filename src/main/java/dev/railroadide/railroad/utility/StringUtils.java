package dev.railroadide.railroad.utility;

import java.time.*;

public class StringUtils {
    // TODO: Probably just delete this, because it doesn't (and can't) support localization
    public static String formatElapsed(long epochMillis) {
        if(epochMillis == -1) {
            return "never";
        }

        Instant then = Instant.ofEpochMilli(epochMillis);
        Instant now  = Instant.now();
        if (then.isAfter(now)) {
            return "in the future";
        }

        // for calendar-accurate months/years
        ZoneId zone = ZoneId.systemDefault();
        LocalDate thenDate = then.atZone(zone).toLocalDate();
        LocalDate nowDate  = LocalDate.now(zone);
        Period period = Period.between(thenDate, nowDate);

        if (period.getYears() > 10) {
            return "more than 10 years ago";
        }

        Duration dur = Duration.between(then, now);
        long seconds = dur.getSeconds();

        if (seconds < 5) {
            return "just now";
        }

        // define the thresholds in descending order
        if (period.getYears() > 0) {
            return formatTime(period.getYears(), "year");
        }

        if (period.getMonths() > 0) {
            return formatTime(period.getMonths(), "month");
        }

        if (period.getDays() >= 7) {
            long weeks = period.getDays() / 7;
            return formatTime(weeks, "week");
        }

        if (period.getDays() > 0) {
            return formatTime(period.getDays(), "day");
        }

        long hours   = dur.toHours();
        if (hours > 0) {
            return formatTime(hours, "hour");
        }

        long minutes = dur.toMinutes();
        if (minutes > 0) {
            return formatTime(minutes, "minute");
        }

        return formatTime(seconds, "second");
    }

    private static String formatTime(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s") + " ago";
    }

    // Take the first character of each word in the alias
    public static String getAbbreviation(String alias) {
        var abbreviation = new StringBuilder();
        for (String word : alias.split(" ")) {
            if (word.isBlank())
                continue;

            abbreviation.append(word.charAt(0));
        }

        return abbreviation.toString();
    }
}
