package dev.railroadide.railroad.ide.ui.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Builds concise, unambiguous labels for open editor tabs. */
public final class EditorTabPresentation {
    private EditorTabPresentation() {
    }

    /**
     * Returns one title per path. Unique filenames are left alone; duplicate filenames
     * receive the shortest parent-path suffix that distinguishes them.
     */
    public static List<String> disambiguatedTitles(List<Path> paths) {
        Objects.requireNonNull(paths, "Paths cannot be null");
        List<Path> normalizedPaths = paths.stream()
            .map(path -> Objects.requireNonNull(path, "Path cannot be null").toAbsolutePath().normalize())
            .toList();
        List<String> titles = normalizedPaths.stream()
            .map(EditorTabPresentation::fileName)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        Map<String, List<Integer>> indexesByName = new HashMap<>();
        for (int index = 0; index < normalizedPaths.size(); index++) {
            indexesByName.computeIfAbsent(comparisonKey(fileName(normalizedPaths.get(index))), _ -> new ArrayList<>())
                .add(index);
        }

        for (List<Integer> duplicateIndexes : indexesByName.values()) {
            if (duplicateIndexes.size() < 2)
                continue;

            for (int index : duplicateIndexes) {
                Path path = normalizedPaths.get(index);
                String suffix = shortestUniqueParentSuffix(path, index, duplicateIndexes, normalizedPaths);
                titles.set(index, fileName(path) + " — " + suffix);
            }
        }
        return List.copyOf(titles);
    }

    public static String fileName(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static String shortestUniqueParentSuffix(
        Path path,
        int pathIndex,
        List<Integer> duplicateIndexes,
        List<Path> paths
    ) {
        Path parent = path.getParent();
        int maximumDepth = parent == null ? 1 : parent.getNameCount() + 1;
        for (int depth = 1; depth <= maximumDepth; depth++) {
            int currentDepth = depth;
            String candidate = parentSuffix(parent, currentDepth);
            boolean unique = duplicateIndexes.stream()
                .filter(index -> index != pathIndex)
                .map(paths::get)
                .map(Path::getParent)
                .map(otherParent -> parentSuffix(otherParent, currentDepth))
                .noneMatch(otherSuffix -> pathsEqual(candidate, otherSuffix));
            if (unique)
                return candidate;
        }
        return parent == null ? path.toString() : parent.toString();
    }

    private static String parentSuffix(Path parent, int depth) {
        if (parent == null)
            return File.separator;

        int nameCount = parent.getNameCount();
        if (depth > nameCount)
            return parent.toString();
        return parent.subpath(nameCount - depth, nameCount).toString();
    }

    private static boolean pathsEqual(String first, String second) {
        return File.separatorChar == '\\'
            ? first.equalsIgnoreCase(second)
            : first.equals(second);
    }

    private static String comparisonKey(String fileName) {
        return File.separatorChar == '\\' ? fileName.toLowerCase(Locale.ROOT) : fileName;
    }
}
