package dev.railroadide.railroad.vcs.git.diff;

import java.util.List;

/**
 * Root container for parsed file diffs.
 *
 * @param files parsed file diffs
 */
public record DiffBlob(List<DiffFile> files) {
}
