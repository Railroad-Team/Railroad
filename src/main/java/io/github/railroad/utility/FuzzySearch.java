package io.github.railroad.utility;

import java.util.List;
import java.util.function.Function;

public class FuzzySearch<D, R> {
    private final D data;
    private final Function<D, List<String>> extractor;
    private final Function<String, R> transformer;

    private String metaphone(String input) {
        input = input.toUpperCase();
        input = input.replaceAll("[^A-Z]", "");

        var sb = new StringBuilder();
        char lastChar = '\0';
        for (char c : input.toCharArray()) {
            if (c != lastChar) {
                sb.append(c);
                lastChar = c;
            }
        }

        return sb.toString()
                .replaceAll("[AEIOU]", "")
                .replaceAll("PH", "F")
                .replaceAll("KN", "N")
                .replaceAll("GH", "H")
                .replaceAll("GN", "N")
                .replaceAll("MB$", "M");
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    /**
     * FuzzySearch constructor
     * @param data Data to search in, e.g a map
     * @param extractor Get a list of search strings from data
     * @param transformer Key to result transformer
     */
    public FuzzySearch(D data, Function<D, List<String>> extractor, Function<String, R> transformer) {
        this.data = data;
        this.extractor = extractor;
        this.transformer = transformer;
    }

    /**
     * Find the closest matching string in the data to the query
     * @param query
     * @return Closest matching string, null if no good match is found
     */
    public R search(String query) {
        String queryMeta = metaphone(query);
        int bestScore = Integer.MAX_VALUE;
        String bestMatch = null;

        for (String key : extractor.apply(data)) {
            String keyMeta = metaphone(key);

            int score = levenshtein(query.toLowerCase(), key.toLowerCase());

            if (queryMeta.equals(keyMeta)) {
                score -= 1;
            }

            for (String substring : key.split(" ")) {
                int substringScore = levenshtein(query.toLowerCase(), substring.toLowerCase());

                if (query.contains(substring)) {
                    //TODO possibly add a system where the uniqueness of a substring is taken into account
                    score -= 200;
                }

                if (substringScore <= Math.max(2, substring.length() / 3)) {
                    score -= 5;
                }
            }

            if (score < bestScore) {
                bestScore = score;
                bestMatch = key;
            }
        }

        int maxDistance = Math.max(2, query.length() / 3);
        if (bestScore > maxDistance) {
            return null;
        }

        return transformer.apply(bestMatch);
    }

    /**
     * Check if two strings are similar
     * @param query
     * @param key
     * @return true if the two strings are similar
     */
    public boolean isSimilar(String query, String key) {
        String queryMeta = metaphone(query);
        String keyMeta = metaphone(key);

        int score = levenshtein(query.toLowerCase(), key.toLowerCase());

        if (queryMeta.equals(keyMeta)) {
            score -= 1;
        }

        for (String substring : key.split(" ")) {
            int substringScore = levenshtein(query.toLowerCase(), substring.toLowerCase());

            if (substringScore <= Math.max(2, substring.length() / 3)) {
                return true;
            }
        }

        int maxDistance = Math.max(2, query.length() / 3);
        return score <= maxDistance;
    }
}
