package dev.railroadide.railroad.ide.indexing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexesTest {
    @Test
    void standardLibraryScanIncludesJavaUtilList() {
        assertTrue(Indexes.scanStandardLibrary().stream()
            .anyMatch(stub -> "java.util.List".equals(stub.getFullName())));
    }
}
