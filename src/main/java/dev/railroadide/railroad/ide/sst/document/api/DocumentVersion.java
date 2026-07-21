package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * Immutable, monotonically ordered revision of one logical document.
 * <p>
 * Versions are scoped to a {@link DocumentId}; equal numeric values belonging to
 * different documents do not imply related content. Every content change must produce
 * a strictly later version for that document. Gaps are valid, while moving or renaming a
 * document without changing its content retains the current version.
 * <p>
 * Instances are immutable, thread-safe value objects. Allocation and progression are
 * owned by the workspace or document service rather than by analysis consumers.
 *
 * @param value non-negative revision value
 */
public record DocumentVersion(long value) implements Comparable<DocumentVersion> {
    private static final DocumentVersion INITIAL = new DocumentVersion(0);

    public DocumentVersion {
        if (value < 0)
            throw new IllegalArgumentException("Document version cannot be negative: " + value);
    }

    /**
     * Returns the initial version assigned to a newly owned document.
     *
     * @return version zero
     */
    public static DocumentVersion initial() {
        return INITIAL;
    }

    /**
     * Parses the canonical decimal representation of a document version.
     *
     * @param value non-negative decimal version
     * @return parsed document version
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if it is blank, malformed, or negative
     */
    public static DocumentVersion parse(String value) {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty())
            throw new IllegalArgumentException("value cannot be blank");

        return new DocumentVersion(Long.parseLong(value));
    }

    /**
     * Returns the immediately following version.
     *
     * @return next document version
     * @throws IllegalStateException if this version is {@link Long#MAX_VALUE}
     */
    public DocumentVersion next() {
        if (value == Long.MAX_VALUE)
            throw new IllegalStateException("Document version is exhausted");
        return new DocumentVersion(value + 1);
    }

    /**
     * Returns whether this version is strictly later than {@code other}.
     *
     * @param other version from the same logical document
     * @return {@code true} when this version is later
     */
    public boolean isAfter(DocumentVersion other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns whether this version is strictly earlier than {@code other}.
     *
     * @param other version from the same logical document
     * @return {@code true} when this version is earlier
     */
    public boolean isBefore(DocumentVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(DocumentVersion other) {
        return Long.compare(value, Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
