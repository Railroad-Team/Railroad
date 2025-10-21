package dev.railroadide.core.utility;

/**
 * A utility class for string-related operations.
 */
public class StringUtils {

    /**
     * A regular expression pattern for validating and matching URLs.
     * This pattern supports both HTTP and HTTPS protocols and includes
     * various URL components such as domain, path, query parameters, etc.
     */
    public static final String URL_REGEX = "(?:http(s)?:\\/\\/)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$";

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
