package dev.railroadide.railroad.ide.sst.document.api;

/**
 * An immutable, half-open range of offsets into a {@link DocumentSnapshot}.
 * The start offset is inclusive and the end offset is exclusive; equal offsets
 * represent an empty range. Offset units are defined by each concrete range type.
 * <p>
 * Construction validates the ordering and non-negative bounds independently of
 * any snapshot. Bounds against a particular snapshot are checked when a concrete
 * range's content view is requested.
 *
 * @param <S> the type of document snapshot supported by this range
 */
public abstract sealed class Range<S extends DocumentSnapshot>
    permits TextRange, BinaryRange {
    /**
     * The inclusive, non-negative start offset of the range.
     */
    public final int start;

    /**
     * The exclusive end offset, greater than or equal to {@link #start}.
     */
    public final int end;

    /**
     * Creates a range with the given half-open bounds.
     *
     * @param start inclusive start offset
     * @param end exclusive end offset
     * @throws IllegalArgumentException if {@code start} is negative or {@code end < start}
     */
    public Range(int start, int end) {
        this.start = start;
        this.end = end;

        if (start < 0)
            throw new IllegalArgumentException("start must be non-negative");
        if (end < start)
            throw new IllegalArgumentException("end must be >= start");
    }

    /**
     * Tests whether an offset lies within this range.
     * An empty range contains no offsets.
     *
     * @param index offset to test, in this range's units
     * @return {@code true} if {@code start <= index && index < end}; otherwise {@code false}
     */
    public boolean contains(int index) {
        return index >= start && index < end;
    }

    /**
     * Returns the length of this range in its offset units.
     *
     * @return {@code end - start}, or zero for an empty range
     */
    public final int length() {
        return end - start;
    }
}
