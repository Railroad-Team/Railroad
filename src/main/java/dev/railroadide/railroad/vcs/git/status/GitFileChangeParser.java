package dev.railroadide.railroad.vcs.git.status;

import dev.railroadide.railroad.vcs.git.util.GitRepository;

import java.nio.file.Path;

/**
 * Parses porcelain status records into {@link GitFileChange} instances.
 */
public final class GitFileChangeParser {
    private GitFileChangeParser() {
    }

    /**
     * Parses one porcelain-v1-z file record.
     *
     * @param repo repository owning the paths
     * @param record current status record
     * @param nextRecord optional next record used for rename/copy source path
     * @return parsed file change, or {@code null} when the record is invalid
     */
    public static GitFileChange parsePorcelainV1ZRecord(GitRepository repo, String record, String nextRecord) {
        if (record == null || record.isEmpty())
            return null;

        if (record.length() < 3)
            return null;

        char x = record.charAt(0);
        char y = record.charAt(1);

        String filePath = record.substring(2).trim();

        // Rename or Copy
        boolean expectsSecondPath = (x == 'R' || x == 'C' || y == 'R' || y == 'C');
        Path repoRoot = repo.root();
        if (expectsSecondPath && nextRecord != null && !nextRecord.isEmpty()) {
            return new GitFileChange(
                repoRoot.resolve(nextRecord).normalize(),
                repoRoot.resolve(filePath).normalize(),
                x, y
            );
        }

        return new GitFileChange(
            repoRoot.resolve(filePath).normalize(),
            x, y
        );
    }
}
