package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

/**
 * A half-open range of addresses, {@code [start, end)}, in an address space of a particular binary.
 * Addresses and range bounds are non-negative signed {@code long} values, up to
 * {@link Long#MAX_VALUE}. The start is inclusive and the end is exclusive; equal bounds
 * represent an empty range.
 * <p>
 * The {@code addressSpaceId} identifies an address space within the context of a particular
 * binary, not just its file format. A virtual address does not necessarily correspond to an
 * offset in the binary file. Mapping virtual addresses to bytes is the responsibility of a
 * binary-format provider.
 *
 * @param addressSpaceId the non-null, non-blank address space identifier within a particular binary
 * @param start the inclusive, non-negative start address
 * @param end the exclusive end address, greater than or equal to {@code start}
 */
public record AddressRange(
    String addressSpaceId,
    long start,
    long end
) {
    public AddressRange {
        Objects.requireNonNull(addressSpaceId, "addressSpaceId must not be null");
        if (addressSpaceId.isBlank())
            throw new IllegalArgumentException("addressSpaceId must not be blank");

        if (start < 0)
            throw new IllegalArgumentException("start must be non-negative");

        if (end < start)
            throw new IllegalArgumentException("end must be >= start");
    }

    /**
     * Returns the length of this half-open range, calculated as {@code end - start}.
     *
     * @return the non-negative range length, or zero for an empty range
     */
    public long length() {
        return end - start;
    }

    /**
     * Tests whether the given address is within this half-open range.
     *
     * @param address the address to test in this range's address space
     * @return {@code true} if {@code start <= address && address < end}; otherwise {@code false}
     */
    public boolean contains(long address) {
        return address >= start && address < end;
    }
}
