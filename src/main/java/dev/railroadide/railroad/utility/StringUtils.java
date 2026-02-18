package dev.railroadide.railroad.utility;

import io.github.palexdev.mfxcore.utils.ExceptionUtils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class StringUtils {
    /**
     * A regular expression pattern for validating and matching URLs.
     * This pattern supports both HTTP and HTTPS protocols and includes
     * various URL components such as domain, path, query parameters, etc.
     */
    public static final String URL_REGEX = "(?:http(s)?:\\/\\/)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$";

    private StringUtils() {
        // Utility class, no instantiation
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

    public static String capitalizeFirstLetterOfEachWord(String input) {
        String[] words = input.split(" ");
        var capitalized = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (!word.isEmpty()) {
                capitalized.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase());

                if (i < words.length - 1) {
                    capitalized.append(" ");
                }
            }
        }

        return capitalized.toString().trim();
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     * The Levenshtein distance is a measure of the minimum number of
     * single-character edits (insertions, deletions, or substitutions)
     * required to change one string into the other.
     *
     * @param a The first string to compare.
     * @param b The second string to compare.
     * @return The Levenshtein distance between the two strings.
     */
    public static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        // Initialize the first row and column of the DP table.
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        // Fill the DP table with the minimum edit distances.
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        // Return the final computed Levenshtein distance.
        return dp[a.length()][b.length()];
    }
}
