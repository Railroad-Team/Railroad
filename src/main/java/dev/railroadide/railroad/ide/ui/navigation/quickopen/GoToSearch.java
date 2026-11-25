package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses go-to queries and ranks candidate items against them.
 */
public final class GoToSearch {
    private static final Pattern LOCATION_SUFFIX = Pattern.compile(":(\\d+)(?::(\\d+))?$");

    private GoToSearch() {
    }

    /**
     * Ranks the items matching a pattern, best first. Patterns containing the contributor's path separator are
     * matched against each item's path; others against its name. Ties are broken by shorter name, then name and path
     * order.
     *
     * @param <T> item type
     * @param contributor source of item names and paths
     * @param items candidates to rank
     * @param pattern typed pattern, without a location suffix
     * @param limit maximum number of results
     * @return ranked matches, at most {@code limit} long
     */
    public static <T> List<Result<T>> rank(GoToContributor<T> contributor, List<T> items, String pattern, int limit) {
        String normalized = contributor.normalizePattern(pattern.strip());
        boolean byPath = normalized.indexOf(contributor.separator()) >= 0;
        List<Result<T>> results = new ArrayList<>();
        for (T item : items) {
            String name = contributor.name(item);
            String path = contributor.path(item);
            NameMatcher.Match match = NameMatcher.match(normalized, byPath ? path : name);
            if (match != null) {
                results.add(new Result<>(item, name, path, match.score(), byPath ? null : match));
            }
        }

        results.sort(Comparator.<Result<T>>comparingInt(Result::score).reversed()
            .thenComparingInt(result -> result.name().length())
            .thenComparing(Result::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Result::path, String.CASE_INSENSITIVE_ORDER));
        return results.size() > limit ? List.copyOf(results.subList(0, limit)) : List.copyOf(results);
    }

    /**
     * Ranked item.
     *
     * @param <T> item type
     * @param item matched item
     * @param name item name shown as the primary text
     * @param path item path used for path matches
     * @param score match quality; higher is better
     * @param nameMatch match against the name, or {@code null} when the pattern matched the path
     */
    public record Result<T>(T item, String name, String path, int score, NameMatcher.@Nullable Match nameMatch) {
    }

    /**
     * Typed query split into a name pattern and an optional {@code :line[:column]} suffix.
     *
     * @param pattern name pattern
     * @param line one-based line, or {@code 0} when absent
     * @param column one-based column, or {@code 0} when absent
     */
    public record Query(String pattern, int line, int column) {
        /**
         * Parses typed text such as {@code Main.java:12} or {@code Main:12:5}.
         *
         * @param text typed text
         * @return parsed query
         */
        public static Query parse(String text) {
            String stripped = text.strip();
            Matcher matcher = LOCATION_SUFFIX.matcher(stripped);
            if (!matcher.find())
                return new Query(stripped, 0, 0);

            try {
                int line = Integer.parseInt(matcher.group(1));
                int column = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                return new Query(stripped.substring(0, matcher.start()), line, column);
            } catch (NumberFormatException exception) {
                return new Query(stripped, 0, 0);
            }
        }

        /**
         * Tests whether the query names a line.
         *
         * @return whether a line was given
         */
        public boolean hasLine() {
            return line > 0;
        }

        /**
         * Converts the line and column to an offset, clamping them to the text.
         *
         * @param text document text
         * @return offset of the location, or {@code -1} when the query names no line
         */
        public int offsetIn(String text) {
            if (!hasLine())
                return -1;

            int lineStart = 0;
            for (int current = 1; current < line; current++) {
                int newline = text.indexOf('\n', lineStart);
                if (newline < 0)
                    break;

                lineStart = newline + 1;
            }

            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            } else if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }

            return Math.min(lineStart + Math.max(column - 1, 0), lineEnd);
        }
    }
}
