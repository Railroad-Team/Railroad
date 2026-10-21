package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Objects;

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

    public long length() {
        return end - start;
    }

    public boolean contains(long address) {
        return address >= start && address < end;
    }
}
