package dev.railroadide.core.utility;

import java.util.List;
import java.util.function.Function;

/**
 * A utility class for performing fuzzy search operations on a dataset.
 * This class uses a combination of phonetic matching (Metaphone algorithm)
 * and Levenshtein distance to find the closest matches to a query string.
 *
 * @param <D> The type of the data to search in.
 * @param <R> The type of the result returned after transformation.
 */
public class FuzzySearch<D, R> {
    private final D data;
    private final Function<D, List<String>> extractor;
    private final Function<String, R> transformer;

    /**
     * Constructs a new FuzzySearch instance.
     *
     * @param data The data to search in, e.g., a map or a list.
     * @param extractor A function to extract a list of search strings from the data.
     * @param transformer A function to transform a matching key into a result.
     */
    public FuzzySearch(D data, Function<D, List<String>> extractor, Function<String, R> transformer) {
        this.data = data;
        this.extractor = extractor;
        this.transformer = transformer;
    }

    /**
     * Applies the Metaphone algorithm to the input string to generate a phonetic representation.
     * This method removes non-alphabetic characters, eliminates duplicate consecutive characters,
     * and applies specific phonetic transformations.
     *
     * @param input The input string to process.
     * @return A phonetic representation of the input string.
     */
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

    /**
     * Finds the closest matching string in the data to the given query.
     * The matching is based on a combination of phonetic similarity and
     * Levenshtein distance.
     *
     * @param query The string to search for.
     * @return The closest matching string, or null if no good match is found.
     */
    public R search(String query) {
        String queryMeta = metaphone(query);
        int bestScore = Integer.MAX_VALUE;
        String bestMatch = null;

        for (String key : extractor.apply(data)) {
            String keyMeta = metaphone(key);

            int score = StringUtils.levenshtein(query.toLowerCase(), key.toLowerCase());

            if (queryMeta.equals(keyMeta)) {
                score -= 1;
            }

            for (String substring : key.split(" ")) {
                int substringScore = StringUtils.levenshtein(query.toLowerCase(), substring.toLowerCase());

                if (query.contains(substring)) {
                    // TODO: possibly add a system where the uniqueness of a substring is taken into account
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
     * Checks if two strings are similar based on phonetic similarity
     * and Levenshtein distance.
     *
     * @param query The string to search for.
     * @param key The string to compare against.
     * @return true if the two strings are considered similar, false otherwise.
     */
    public boolean isSimilar(String query, String key) {
        String queryMeta = metaphone(query);
        String keyMeta = metaphone(key);

        int score = StringUtils.levenshtein(query.toLowerCase(), key.toLowerCase());

        if (queryMeta.equals(keyMeta)) {
            score -= 1;
        }

        for (String substring : key.split(" ")) {
            int substringScore = StringUtils.levenshtein(query.toLowerCase(), substring.toLowerCase());

            if (substringScore <= Math.max(2, substring.length() / 3)) {
                return true;
            }
        }

        int maxDistance = Math.max(2, query.length() / 3);
        return score <= maxDistance;
    }
}
