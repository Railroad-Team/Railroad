package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Case-insensitive fuzzy matching of a typed pattern against a name, favouring matches at word starts such as
 * camel-case humps and the characters after separators, so that {@code FRB} and {@code findRep} both match
 * {@code FindReplaceBar}.
 */
public final class NameMatcher {
    private static final int MATCH = 1;
    private static final int EXACT_CASE = 1;
    private static final int WORD_START = 8;
    private static final int CONSECUTIVE = 5;
    private static final int LEADING = 10;
    private static final int PREFIX = 30;
    private static final int EQUAL = 100;

    private NameMatcher() {
    }

    /**
     * Matches every pattern character, in order, against the text.
     *
     * @param pattern typed pattern; an empty pattern matches everything with a zero score
     * @param text candidate text
     * @return best match found, or {@code null} when the text does not contain the pattern as a subsequence
     */
    public static @Nullable Match match(String pattern, String text) {
        if (pattern.isEmpty())
            return new Match(0, new int[0]);

        if (pattern.length() > text.length())
            return null;

        String lowerPattern = pattern.toLowerCase(Locale.ROOT);
        String lowerText = text.toLowerCase(Locale.ROOT);
        int[] humps = humpPositions(lowerPattern, text, lowerText);
        if (humps == null) {
            humps = subsequencePositions(lowerPattern, lowerText);
        }

        if (humps == null)
            return null;

        var best = new Match(score(pattern, text, humps), humps);
        int[] substring = substringPositions(lowerPattern, text, lowerText);
        if (substring != null) {
            int substringScore = score(pattern, text, substring);
            if (substringScore > best.score()) {
                best = new Match(substringScore, substring);
            }
        }

        return best;
    }

    /**
     * Tests whether a character begins a word: the first character, an upper-case letter after a lower-case letter or
     * digit, a letter or digit after a separator, or a letter after a digit.
     *
     * @param text text containing the character
     * @param index index of the character
     * @return whether the character starts a word
     */
    public static boolean isWordStart(String text, int index) {
        if (index == 0)
            return true;

        char current = text.charAt(index);
        char previous = text.charAt(index - 1);
        if (!Character.isLetterOrDigit(current))
            return false;

        if (!Character.isLetterOrDigit(previous))
            return true;

        if (Character.isUpperCase(current) && !Character.isUpperCase(previous))
            return true;

        return Character.isLetter(current) && Character.isDigit(previous);
    }

    private static int @Nullable [] humpPositions(String lowerPattern, String text, String lowerText) {
        int[] positions = new int[lowerPattern.length()];
        int from = 0;
        for (int index = 0; index < lowerPattern.length(); index++) {
            char wanted = lowerPattern.charAt(index);
            int found = -1;
            if (index > 0 && from < lowerText.length() && lowerText.charAt(from) == wanted) {
                found = from;
            } else {
                for (int candidate = from; candidate < lowerText.length(); candidate++) {
                    if (lowerText.charAt(candidate) == wanted && isWordStart(text, candidate)) {
                        found = candidate;
                        break;
                    }
                }

                if (found < 0) {
                    found = lowerText.indexOf(wanted, from);
                }
            }

            if (found < 0)
                return null;

            positions[index] = found;
            from = found + 1;
        }

        return positions;
    }

    private static int @Nullable [] subsequencePositions(String lowerPattern, String lowerText) {
        int[] positions = new int[lowerPattern.length()];
        int from = 0;
        for (int index = 0; index < lowerPattern.length(); index++) {
            int found = lowerText.indexOf(lowerPattern.charAt(index), from);
            if (found < 0)
                return null;

            positions[index] = found;
            from = found + 1;
        }

        return positions;
    }

    private static int @Nullable [] substringPositions(String lowerPattern, String text, String lowerText) {
        int start = lowerText.indexOf(lowerPattern);
        if (start < 0)
            return null;

        for (int candidate = start; candidate >= 0; candidate = lowerText.indexOf(lowerPattern, candidate + 1)) {
            if (isWordStart(text, candidate)) {
                start = candidate;
                break;
            }
        }

        int[] positions = new int[lowerPattern.length()];
        for (int index = 0; index < positions.length; index++) {
            positions[index] = start + index;
        }

        return positions;
    }

    private static int score(String pattern, String text, int[] positions) {
        int score = 0;
        for (int index = 0; index < positions.length; index++) {
            int position = positions[index];
            score += MATCH;
            if (text.charAt(position) == pattern.charAt(index)) {
                score += EXACT_CASE;
            }

            if (isWordStart(text, position)) {
                score += WORD_START;
            }

            if (index > 0 && positions[index - 1] == position - 1) {
                score += CONSECUTIVE;
            }
        }

        if (positions[0] == 0) {
            score += LEADING;
        }

        if (text.equalsIgnoreCase(pattern)) {
            score += EQUAL;
        } else if (text.regionMatches(true, 0, pattern, 0, pattern.length())) {
            score += PREFIX;
        }

        return score - (text.length() - pattern.length()) / 4;
    }

    /**
     * Successful match of a pattern.
     *
     * @param score relative quality; higher is better
     * @param positions text indices matched by each pattern character, in ascending order
     */
    public record Match(int score, int[] positions) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Match match && score == match.score
                && Arrays.equals(positions, match.positions);
        }

        @Override
        public int hashCode() {
            return 31 * score + Arrays.hashCode(positions);
        }

        @Override
        public String toString() {
            return "Match[score=" + score + ", positions=" + Arrays.toString(positions) + "]";
        }
    }
}
