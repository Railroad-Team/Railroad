package dev.railroadide.railroad.ide.ui.editor;

import java.util.Deque;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Selects eviction candidates and bounds editor tab history by recency.
 */
public final class EditorTabRetentionPolicy {
    private EditorTabRetentionPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a nullable retention limit to a nonnegative value.
     *
     * @param limit maximum number of entries; null or negative values are treated as zero
     * @return zero for null or negative limits, otherwise the supplied limit
     */
    public static int normalizeLimit(Integer limit) {
        return limit == null ? 0 : Math.max(0, limit);
    }

    /**
     * Finds the first eligible entry in least-recently-used order, skipping excluded entries.
     *
     * @param <T> type of retained entry
     * @param leastRecentlyUsedFirst candidates ordered from least to most recently used
     * @param excluded entries that must not be selected
     * @param evictable predicate identifying entries eligible for eviction
     * @return least recently used eligible entry, or null when none qualifies
     */
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

    /**
     * Removes the oldest entries from the deque until it fits the normalized limit.
     *
     * @param <T> type of retained entry
     * @param entries deque ordered from most to least recently used
     * @param limit maximum number of entries; null or negative values are treated as zero
     */
    public static <T> void trimMostRecentFirst(Deque<T> entries, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        while (entries.size() > normalizedLimit) {
            entries.removeLast();
        }
    }
}
