package dev.railroadide.railroad.ide.sst.document.api;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable, opaque identity of a logical document.
 * <p>
 * A document ID is independent of the document's location, content, language, and
 * revision. Moving or editing a document therefore does not change its ID. Copies and
 * newly created documents receive new IDs even when their contents are identical.
 * <p>
 * Instances are immutable, thread-safe value objects. The canonical external form is
 * the lowercase UUID returned by {@link #toString()} and accepted by {@link #parse(String)}.
 * Allocation and lifecycle are owned by the workspace or document service; consumers
 * should carry IDs supplied by that owner rather than manufacture replacements.
 *
 * @param value opaque UUID value
 */
public record DocumentId(UUID value) {
    public DocumentId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Allocates a fresh identity for a new logical document.
     *
     * @return a new document identity
     */
    public static DocumentId create() {
        return new DocumentId(UUID.randomUUID());
    }

    /**
     * Parses the canonical external representation of a document identity.
     *
     * @param value UUID text previously returned by {@link #toString()}
     * @return parsed document identity
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank or not a UUID
     */
    public static DocumentId parse(String value) {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty())
            throw new IllegalArgumentException("value cannot be blank");

        return new DocumentId(UUID.fromString(value));
    }

    @Override
    public @NonNull String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof DocumentId)
            return this.value.equals(((DocumentId)other).value);
        return false;
    }
}
