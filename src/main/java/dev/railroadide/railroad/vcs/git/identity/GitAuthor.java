package dev.railroadide.railroad.vcs.git.identity;

import dev.railroadide.railroad.Railroad;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author summary entry produced from git shortlog output.
 *
 * @param commitCount number of commits by the author
 * @param name author name
 * @param email author email, when available
 */
public record GitAuthor(int commitCount, String name, @Nullable String email) {
    private static final Pattern AUTHOR_LINE_PATTERN = Pattern.compile("^\\s*(?<count>\\d+)\\s+(?<name>.*?)\\s+(<(?<email>.*)?>)\\s*$");

    /**
     * Parses shortlog lines into author summaries.
     *
     * @param lines lines from `git shortlog -sne`
     * @param includeEmail whether parsed records should include email addresses
     * @return parsed author records
     */
    public static List<GitAuthor> parseAuthorsFromShortlogLines(String[] lines, boolean includeEmail) {
        List<GitAuthor> authors = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            Matcher matcher = AUTHOR_LINE_PATTERN.matcher(line);
            if(matcher.find()) {
                String commitsStr = matcher.group("count");
                int commits;
                try {
                    commits = Integer.parseInt(commitsStr);
                } catch (NumberFormatException exception) {
                    Railroad.LOGGER.warn("Failed to parse commit count '{}' in git shortlog line: {}", commitsStr, line);
                    commits = 0;
                }

                String name = matcher.group("name");
                String email = includeEmail ? matcher.group("email") : null;
                authors.add(new GitAuthor(commits, name, email));
            } else {
                Railroad.LOGGER.warn("Failed to parse git shortlog line: {}", line);
            }
        }

        return authors;
    }
}
