package dev.railroadide.railroad.vcs.git.diff;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Structured diff information for a single file.
 *
 * @param oldPath old file path
 * @param newPath new file path
 * @param isBinary whether file is binary
 * @param headers raw file-level diff headers
 * @param hunks parsed text hunks
 */
public record DiffFile(
    @Nullable Path oldPath,
    @Nullable Path newPath,
    boolean isBinary,
    List<String> headers,
    List<DiffHunk> hunks
) {
    /**
     * Returns whether the file was newly added in this diff.
     *
     * @return {@code true} when the old path is absent
     */
    public boolean isNewFile() {
        return oldPath == null;
    }

    /**
     * Returns whether the file was deleted in this diff.
     *
     * @return {@code true} when the new path is absent
     */
    public boolean isDeletedFile() {
        return newPath == null;
    }

    /**
     * Returns a copy marked as binary.
     *
     * @param file source file diff
     * @return copied diff marked as binary
     */
    public static DiffFile binary(DiffFile file) {
        return new DiffFile(
            file.oldPath,
            file.newPath,
            true,
            file.headers,
            file.hunks
        );
    }

    /**
     * Returns a copy with an updated old path.
     *
     * @param file source file diff
     * @param oldPath replacement old path text
     * @return copied diff with the updated old path
     */
    public static DiffFile setOldPath(DiffFile file, String oldPath) {
        return new DiffFile(
            Path.of(oldPath).normalize(),
            file.newPath,
            file.isBinary,
            file.headers,
            file.hunks
        );
    }

    /**
     * Returns a copy with an updated new path.
     *
     * @param file source file diff
     * @param newPath replacement new path text
     * @return copied diff with the updated new path
     */
    public static DiffFile setNewPath(DiffFile file, String newPath) {
        return new DiffFile(
            file.oldPath,
            Path.of(newPath).normalize(),
            file.isBinary,
            file.headers,
            file.hunks
        );
    }
}
