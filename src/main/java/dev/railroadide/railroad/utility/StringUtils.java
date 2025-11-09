package dev.railroadide.railroad.utility;

import io.github.palexdev.mfxcore.utils.ExceptionUtils;
import org.jetbrains.annotations.Nullable;

import java.time.*;
import java.util.HashMap;
import java.util.Map;

public final class StringUtils {
    private StringUtils() {
        // Utility class, no instantiation
    }

    /**
     * Formats a given epoch time in milliseconds into a human-readable string that indicates
     * how long ago that time was from the current time.
     * <p>
     * TODO: Probably just delete this, because it doesn't (and can't) support localization
     *
     * @param epochMillis The epoch time in milliseconds to format.
     * @return A human-readable string indicating the elapsed time, or "never" if the input is -1.
     */
    public static String formatElapsed(long epochMillis) {
        if (epochMillis == -1) {
            return "never";
        }

        Instant then = Instant.ofEpochMilli(epochMillis);
        Instant now = Instant.now();
        if (then.isAfter(now)) {
            return "in the future";
        }

        // for calendar-accurate months/years
        ZoneId zone = ZoneId.systemDefault();
        LocalDate thenDate = then.atZone(zone).toLocalDate();
        LocalDate nowDate = LocalDate.now(zone);
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

        long hours = dur.toHours();
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

    /**
     * Generates an abbreviation from a given alias by taking the first character of each word.
     *
     * @param alias The alias to generate an abbreviation from.
     * @return The generated abbreviation.
     */
    public static String getAbbreviation(String alias) {
        var abbreviation = new StringBuilder();
        for (String word : alias.split(" ")) {
            if (word.isBlank())
                continue;

            abbreviation.append(word.charAt(0));
        }

        return abbreviation.toString();
    }

    public static String exceptionToString(Throwable exception) {
        var sb = new StringBuilder();
        sb.append(ExceptionUtils.formatException(exception));
        Throwable cause = exception.getCause();
        while (cause != null) {
            sb.append("\nCaused by: ").append(ExceptionUtils.formatException(cause));
            cause = cause.getCause();
        }

        return sb.toString();
    }

    /**
     * Converts a map of environment variables to a string representation.
     *
     * @param envVars The map of environment variables.
     * @return A string representation of the environment variables.
     */
    public static String environmentVariablesToString(@Nullable Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty())
            return "";

        var stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : envVars.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }

        return stringBuilder.toString();
    }

    /**
     * Validates if a given string is a valid representation of environment variables.
     * The expected format is "KEY1=VALUE1;KEY2=VALUE2;...".
     *
     * @param text The string to validate.
     * @return True if the string is valid, false otherwise.
     */
    public static boolean isValidEnvironmentVariablesString(@Nullable String text) {
        if (text == null)
            return false;

        if (text.isBlank())
            return true;

        String[] pairs = text.split(";");
        for (String pair : pairs) {
            if (pair.isBlank())
                continue;

            // TODO: Handle cases where the value might contain '='
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length != 2 || keyValue[0].isBlank())
                return false;
        }

        return true;
    }

    /**
     * Converts an array of strings into a single string, with each element separated by the specified delimiter.
     *
     * @param vmOptions The array of strings to convert.
     * @param delimiter The delimiter to use between elements.
     * @return A single string with elements separated by the delimiter.
     */
    public static String stringArrayToString(String[] vmOptions, String delimiter) {
        if (vmOptions == null || vmOptions.length == 0)
            return "";

        return String.join(delimiter, vmOptions);
    }

    /**
     * Converts a string representation of environment variables into a map.
     * The expected format is "KEY1=VALUE1;KEY2=VALUE2;...".
     *
     * @param environmentVariables The string representation of environment variables.
     * @return A map of environment variables.
     */
    public static Map<String, String> stringToEnvironmentVariables(String environmentVariables) {
        Map<String, String> envVars = new HashMap<>();
        if (environmentVariables == null || environmentVariables.isBlank())
            return envVars;

        String[] pairs = environmentVariables.split(";");
        for (String pair : pairs) {
            if (pair.isBlank())
                continue;

            // TODO: Handle cases where the value might contain '='
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                envVars.put(keyValue[0], keyValue[1]);
            }
        }

        return envVars;
    }

    /**
     * Splits a string into an array of strings based on the specified delimiter.
     *
     * @param str       The string to split.
     * @param delimiter The delimiter to use for splitting.
     * @return An array of strings obtained by splitting the input string.
     */
    public static String[] stringToStringArray(String str, String delimiter) {
        if (str == null || str.isBlank())
            return new String[0];

        return str.split(delimiter);
    }
}
