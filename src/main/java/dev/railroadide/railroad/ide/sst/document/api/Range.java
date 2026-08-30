package dev.railroadide.railroad.ide.sst.document.api;

/**
 * A validated, half-open range of content within a {@link DocumentSnapshot}.
 *
 * @param <S> The type of {@link DocumentSnapshot} supported
 */
public abstract sealed class Range<S extends DocumentSnapshot>
    permits TextRange, BinaryRange {
    /** Inclusive start of the range */
    public final int start;

    /** Exclusive end of the range */
    public final int end;

    public Range(int start, int end) {
        this.start = start;
        this.end = end;

        if (start < 0)
            throw new IllegalArgumentException("start must be non-negative");
        if (end < start)
            throw new IllegalArgumentException("end must be >= start");
    }

    public boolean contains(int index) {
        return index >= start && index < end;
    }

    public final int length() {
        return end - start;
    }
}
