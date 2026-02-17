package dev.railroadide.railroad.vcs.git.commit;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A page of commit results with an optional cursor for the next page.
 *
 * @param commits commits in this page
 * @param nextCursor cursor for the next page, when present
 */
public record GitCommitPage(List<GitCommit> commits, @Nullable String nextCursor) {}
