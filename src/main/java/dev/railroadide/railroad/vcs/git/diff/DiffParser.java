package dev.railroadide.railroad.vcs.git.diff;

import dev.railroadide.railroad.Railroad;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts raw unified diff text into a structured {@link DiffBlob}.
 */
public final class DiffParser {
    private DiffParser() {
    }

    /**
     * Parses a raw unified diff string.
     *
     * @param rawDiff raw diff text from git
     * @return parsed diff structure
     */
    public static DiffBlob parseDiff(String rawDiff) {
        List<DiffFile> files = new ArrayList<>();

        DiffFile currentFile = null;
        DiffHunk currentHunk = null;
        Integer oldLineCounter = null;
        Integer newLineCounter = null;

        String[] lines = rawDiff.split("\r?\n");
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                //noinspection ConstantValue
                if (currentHunk != null && currentFile != null) {
                    currentFile.hunks().add(currentHunk);
                    currentHunk = null;
                }

                if (currentFile != null) {
                    files.add(currentFile);
                }

                String[] parts = line.split(" ");
                String oldPath = stripPrefix(parts[2], "a/");
                String newPath = stripPrefix(parts[3], "b/");
                Path normalizedOldPath = Path.of(oldPath).normalize();
                Path normalizedNewPath = Path.of(newPath).normalize();

                currentFile = new DiffFile(normalizedOldPath, normalizedNewPath, false, new ArrayList<>(), new ArrayList<>());
            } else if (currentFile != null && line.startsWith("@@ ")) {
                if (currentHunk != null) {
                    currentFile.hunks().add(currentHunk);
                }

                int secondAtIndex = line.indexOf(" @@", 3);
                String hunkHeader = line.substring(3, secondAtIndex).trim();
                String[] hunkParts = hunkHeader.split(" ");
                String oldHunkInfo = hunkParts[0];
                String newHunkInfo = hunkParts[1];
                String[] oldHunkSplit = oldHunkInfo.substring(1).split(",");
                String[] newHunkSplit = newHunkInfo.substring(1).split(",");
                int oldStart = Integer.parseInt(oldHunkSplit[0]);
                int newStart = Integer.parseInt(newHunkSplit[0]);
                int oldLength = oldHunkSplit.length > 1 ? Integer.parseInt(oldHunkSplit[1]) : 1;
                int newLength = newHunkSplit.length > 1 ? Integer.parseInt(newHunkSplit[1]) : 1;

                String sectionHeader = line.substring(secondAtIndex + 3).trim();
                currentHunk = new DiffHunk(oldStart, newStart, oldLength, newLength, sectionHeader, new ArrayList<>());
                oldLineCounter = oldStart;
                newLineCounter = newStart;
            } else if (currentHunk != null) {
                if (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-")) {
                    char prefix = line.charAt(0);
                    String content = line.substring(1);

                    DiffHunkLine.LineType lineType = switch (prefix) {
                        case ' ' -> DiffHunkLine.LineType.CONTEXT;
                        case '+' -> DiffHunkLine.LineType.ADDITION;
                        case '-' -> DiffHunkLine.LineType.DELETION;
                        default -> throw new IllegalStateException("Unexpected line prefix: " + prefix);
                    };

                    Integer oldLineNumber = null;
                    Integer newLineNumber = null;
                    if (lineType == DiffHunkLine.LineType.CONTEXT) {
                        oldLineNumber = oldLineCounter++;
                        newLineNumber = newLineCounter++;
                    } else if (lineType == DiffHunkLine.LineType.ADDITION) {
                        newLineNumber = newLineCounter++;
                    } else {
                        oldLineNumber = oldLineCounter++;
                    }

                    currentHunk.lines().add(new DiffHunkLine(
                        lineType,
                        oldLineNumber,
                        newLineNumber,
                        content,
                        false
                    ));
                } else if (line.startsWith("\\ No newline at end of file")) {
                    if (!currentHunk.lines().isEmpty()) {
                        currentHunk.lines().add(DiffHunkLine.noNewlineAtEnd(currentHunk.lines().removeLast()));
                    } else {
                        Railroad.LOGGER.warn("No lines in hunk to apply 'No newline at end of file' to.");
                    }
                } else {
                    // Unrecognized line in hunk
                    Railroad.LOGGER.warn("Unrecognized line in diff hunk: {}", line);
                }
            } else if (currentFile != null) {
                if (line.startsWith("Binary files ")) {
                    currentFile = DiffFile.binary(currentFile);
                } else {
                    currentFile.headers().add(line);
                    String path = line.substring(4).trim();
                    if (line.startsWith("---")) {
                        if (path.equals("/dev/null"))
                            continue;

                        String oldPath = stripPrefix(path, "a/");
                        currentFile = DiffFile.setOldPath(currentFile, oldPath);
                    } else if (line.startsWith("+++")) {
                        if (path.equals("/dev/null"))
                            continue;

                        String newPath = stripPrefix(path, "b/");
                        currentFile = DiffFile.setNewPath(currentFile, newPath);
                    }
                }
            }
        }

        //noinspection ConstantValue
        if (currentHunk != null && currentFile != null) {
            currentFile.hunks().add(currentHunk);
        }

        if (currentFile != null) {
            files.add(currentFile);
        }

        return new DiffBlob(files);
    }

    private static String stripPrefix(String str, String prefix) {
        if (str.startsWith(prefix))
            return str.substring(prefix.length());

        return str;
    }
}
