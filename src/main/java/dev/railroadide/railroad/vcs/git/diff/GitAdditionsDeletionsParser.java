package dev.railroadide.railroad.vcs.git.diff;

import java.util.List;

/**
 * Parses numstat-style additions and deletions summaries from git output.
 */
public final class GitAdditionsDeletionsParser {
    private GitAdditionsDeletionsParser() {
    }

    /**
     * Parses a single numstat line into path and counts.
     *
     * @param line single numstat line
     * @return parsed additions/deletions entry
     * @throws IllegalArgumentException when the line cannot be parsed
     */
    public static GitAdditionsDeletions parseAdditionsDeletions(String line) {
        if (line == null || line.isEmpty())
            throw new IllegalArgumentException("Input line cannot be null or empty");

        String[] parts = line.split("\\s+");
        if (parts.length < 3)
            throw new IllegalArgumentException("Invalid git diff summary line: " + line);

        int additions = parseCount(parts[0]);
        int deletions = parseCount(parts[1]);
        String path = line.substring(line.indexOf(parts[2]));
        return new GitAdditionsDeletions(path, additions, deletions);
    }

    /**
     * Parses multiple numstat lines.
     *
     * @param lines numstat lines
     * @return parsed additions/deletions entries
     */
    public static List<GitAdditionsDeletions> parseAdditionsDeletions(List<String> lines) {
        if (lines == null || lines.isEmpty())
            return List.of();

        return lines.stream()
            .map(GitAdditionsDeletionsParser::parseAdditionsDeletions)
            .toList();
    }

    private static int parseCount(String countStr) {
        try {
            return Integer.parseInt(countStr);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid count in git diff summary: " + countStr, exception);
        }
    }
}
