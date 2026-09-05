package dev.railroadide.railroad.vcs.git.diff;

import java.util.List;

/**
 * Parsed hunk metadata and lines for a single diff section.
 *
 * @param oldStart starting line in old file
 * @param oldCount number of lines from old file
 * @param newStart starting line in new file
 * @param newCount number of lines from new file
 * @param sectionHeader optional hunk section header
 * @param lines hunk lines
 */
public record DiffHunk(
    int oldStart,
    int oldCount,
    int newStart,
    int newCount,
    String sectionHeader,
    List<DiffHunkLine> lines
) {
}
