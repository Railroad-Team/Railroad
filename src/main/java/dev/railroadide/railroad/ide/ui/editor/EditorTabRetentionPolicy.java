package dev.railroadide.railroad.ide.ui.editor;

import java.util.Deque;
import java.util.Set;
import java.util.function.Predicate;

public final class EditorTabRetentionPolicy {
    private EditorTabRetentionPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static int normalizeLimit(Integer limit) {
        return limit == null ? 0 : Math.max(0, limit);
    }

    public static <T> T findLeastRecentlyUsedEvictable(
        Iterable<T> leastRecentlyUsedFirst,
        Set<T> excluded,
        Predicate<T> evictable
    ) {
        for (T candidate : leastRecentlyUsedFirst) {
            if (!excluded.contains(candidate) && evictable.test(candidate))
                return candidate;
        }
        return null;
    }

    public static <T> void trimMostRecentFirst(Deque<T> entries, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        while (entries.size() > normalizedLimit) {
            entries.removeLast();
        }
    }
}
