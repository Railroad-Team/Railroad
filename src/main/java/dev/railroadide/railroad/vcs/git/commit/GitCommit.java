package dev.railroadide.railroad.vcs.git.commit;

import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable representation of a git commit and selected metadata.
 *
 * @param hash full commit hash
 * @param shortHash shortened commit hash
 * @param subject commit subject line
 * @param authorName author name
 * @param authorEmail author email
 * @param authorTimestampEpochSeconds author timestamp in epoch seconds
 * @param committerName committer name
 * @param committerEmail committer email
 * @param committerTimestampEpochSeconds committer timestamp in epoch seconds
 * @param parentHashes parent commit hashes
 * @param body commit body text
 */
public record GitCommit(
    String hash,
    String shortHash,
    String subject,
    String authorName,
    String authorEmail,
    long authorTimestampEpochSeconds,

    String committerName,
    String committerEmail,
    long committerTimestampEpochSeconds,

    List<String> parentHashes,
    @Nullable String body
) {
    /**
     * Creates a commit with only author metadata and optional parents.
     *
     * @param hash full commit hash
     * @param shortHash shortened display hash
     * @param subject commit subject line
     * @param authorName author name
     * @param authorEmail author email
     * @param authorTimestamp author timestamp in epoch seconds
     * @param parentHashes parent commit hashes
     */
    public GitCommit(String hash, String shortHash, String subject, String authorName, String authorEmail, long authorTimestamp, String... parentHashes) {
        this(
            hash,
            shortHash,
            subject,
            authorName,
            authorEmail,
            authorTimestamp,
            null,
            null,
            0L,
            List.of(parentHashes),
            null
        );
    }

    /**
     * Parses `git show-ref --tags` output into tag-to-commit mappings.
     *
     * @param stdout command output
     * @return map of tag name to commit hash
     */
    public static @NonNull Map<String, String> parseTagsToCommit(String stdout) {
        Map<String, String> tagToCommit = new HashMap<>();
        for (String line : stdout.split("\n")) {
            int spaceIndex = line.indexOf(' ');
            if (spaceIndex <= 0)
                continue;

            String hash = line.substring(0, spaceIndex);
            String ref = line.substring(spaceIndex + 1);
            if (!ref.startsWith("refs/tags/"))
                continue;

            boolean peeled = ref.endsWith("^{}");
            String tagName = ref.substring("refs/tags/".length(), peeled ? ref.length() - 3 : ref.length());
            if (peeled || !tagToCommit.containsKey(tagName)) {
                tagToCommit.put(tagName, hash);
            }
        }
        return tagToCommit;
    }

    /**
     * Returns a copy of the commit with a populated body.
     *
     * @param commit source commit
     * @param body commit body text
     * @return copied commit containing the supplied body
     */
    public static GitCommit withBody(GitCommit commit, String body) {
        return new GitCommit(
            commit.hash,
            commit.shortHash,
            commit.subject,
            commit.authorName,
            commit.authorEmail,
            commit.authorTimestampEpochSeconds,
            commit.committerName,
            commit.committerEmail,
            commit.committerTimestampEpochSeconds,
            commit.parentHashes,
            body
        );
    }
}
