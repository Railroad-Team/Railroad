package dev.railroadide.railroad.vcs.git.commit;

import dev.railroadide.railroad.vcs.git.status.GitFileChange;

import java.util.List;

/**
 * User-provided data for creating or amending a commit.
 *
 * @param message commit summary line
 * @param description optional commit body/description
 * @param amend whether commit should amend previous commit
 * @param signOff whether sign-off should be added
 * @param selectedChanges selected changes to include
 */
public record GitCommitData(
    String message,
    String description,
    boolean amend,
    boolean signOff,
    List<GitFileChange> selectedChanges) {
}
