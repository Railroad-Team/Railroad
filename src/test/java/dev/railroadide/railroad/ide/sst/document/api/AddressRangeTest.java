package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressRangeTest {
    @Test
    void handlesAddressRanges() {
        var range = new AddressRange("elf-virtual", 0x401000, 0x401100);

        assertEquals(256, range.length());
        assertTrue(range.contains(0x401000));
        assertTrue(range.contains(0x4010FF));
        assertFalse(range.contains(0x400FFF));
        assertFalse(range.contains(0x401100));
    }

    @Test
    void supportsEmptyRanges() {
        var range = new AddressRange("elf-virtual", 100, 100);

        assertEquals(0, range.length());
        assertFalse(range.contains(100));
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(NullPointerException.class, () -> new AddressRange(null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new AddressRange("  ", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new AddressRange("elf-virtual", -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new AddressRange("elf-virtual", 10, 5));
    }
}
