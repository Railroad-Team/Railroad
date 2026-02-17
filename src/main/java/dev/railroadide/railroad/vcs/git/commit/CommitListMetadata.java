package dev.railroadide.railroad.vcs.git.commit;

import java.util.List;
import java.util.Map;

/**
 * Metadata associated with a commit list query.
 *
 * @param headCommitHash current HEAD hash
 * @param tagsByCommit map of commit hash to tag names
 */
public record CommitListMetadata(String headCommitHash, Map<String, List<String>> tagsByCommit) {
}
