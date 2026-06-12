package dev.railroadide.railroad.vcs.git.commit;

import dev.railroadide.railroad.vcs.git.GitLog;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for structured commit output produced by git log commands.
 */
public final class GitCommitParser {
    private GitCommitParser() {
    }

    /**
     * Parses delimited commit output into a commit page.
     *
     * @param content delimited commit payload
     * @param limit requested page size
     * @return parsed commit page with optional next cursor
     */
    public static GitCommitPage parseCommits(String content, int limit) {
        if (content == null || content.isBlank())
            return new GitCommitPage(Collections.emptyList(), null);

        content = content.trim();
        String[] commits = content.split("\u001E");
        List<GitCommit> commitList = new ArrayList<>();
        for (String commit : commits) {
            if (commit.isBlank())
                continue;

            commit = commit.strip();
            String[] fields = commit.split("\u0000", -1);
            if (fields.length < 7) {
                GitLog.LOGGER.warn("Malformed git commit entry: {}", commit);
                continue;
            }

            try {
                commitList.add(parseCommit(fields));
            } catch (Exception exception) {
                GitLog.LOGGER.warn("Failed to parse git commit entry: {}", commit, exception);
            }
        }

        String nextCursor = (commitList.size() == limit) ? commitList.getLast().hash() : null;
        return new GitCommitPage(commitList, nextCursor);
    }

    private static @NonNull GitCommit parseCommit(String[] fields) {
        String hash = fields[0].trim();
        if (hash.isBlank())
            throw new IllegalArgumentException("Commit hash cannot be blank");

        String shortHash = fields[1];
        if (shortHash.isBlank()) {
            shortHash = hash.length() >= 7 ? hash.substring(0, 7) : hash;
        }

        String subject = fields[2];

        String authorName = fields[3];
        String authorEmail = fields[4];
        long authorTimestamp = Long.parseLong(fields[5]);

        if (fields.length >= 10) {
            String committerName = fields[6];
            String committerEmail = fields[7];
            long committerTimestamp = fields[8].isBlank() ? 0L : Long.parseLong(fields[8]);
            String parentHashesRaw = fields[9];
            String[] parentHashes = parentHashesRaw.isBlank() ? new String[0] : parentHashesRaw.split("\\s+");

            return new GitCommit(
                hash,
                shortHash,
                subject,
                authorName,
                authorEmail,
                authorTimestamp,
                committerName,
                committerEmail,
                committerTimestamp,
                List.of(parentHashes),
                null
            );
        }

        String parentHashesRaw = fields[6];
        String[] parentHashes = parentHashesRaw.isBlank() ? new String[0] : parentHashesRaw.split("\\s+");
        return new GitCommit(hash, shortHash, subject, authorName, authorEmail, authorTimestamp, parentHashes);
    }
}
