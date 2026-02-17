package dev.railroadide.railroad.vcs.git.diff;

import org.jetbrains.annotations.Nullable;

/**
 * A single line entry in a parsed diff hunk.
 *
 * @param type line type
 * @param oldLineNumber original file line number, when applicable
 * @param newLineNumber updated file line number, when applicable
 * @param content line text without diff prefix
 * @param noNewlineAtEnd whether the line is marked with no trailing newline
 */
public record DiffHunkLine(
    LineType type,
    @Nullable Integer oldLineNumber,
    @Nullable Integer newLineNumber,
    String content,
    boolean noNewlineAtEnd
) {
    /**
     * Returns a copy with the no-newline marker enabled.
     *
     * @param line source hunk line
     * @return copied line with {@code noNewlineAtEnd} set to {@code true}
     */
    public static DiffHunkLine noNewlineAtEnd(DiffHunkLine line) {
        return new DiffHunkLine(
            line.type(),
            line.oldLineNumber(),
            line.newLineNumber(),
            line.content(),
            true
        );
    }

    /**
     * Type of line represented in a diff hunk.
     */
    public enum LineType {
        CONTEXT,
        ADDITION,
        DELETION
    }
}
