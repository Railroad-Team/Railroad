package dev.railroadide.railroad.ide.indexing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexesTest {
    @Test
    void standardLibraryScanIncludesClassesOutsideJavaBase() {
        var stubs = Indexes.scanStandardLibrary();
        var qualifiedNames = stubs.stream()
            .map(stub -> stub.getFullName())
            .collect(java.util.stream.Collectors.toSet());

        assertTrue(qualifiedNames.contains("java.util.List"));
        assertTrue(qualifiedNames.contains("java.sql.Connection"));
        assertTrue(qualifiedNames.contains("java.net.http.HttpClient"));

        boolean runtimeHasClamp = java.util.Arrays.stream(Math.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("clamp"));
        if (runtimeHasClamp) {
            var math = stubs.stream()
                .filter(stub -> "java.lang.Math".equals(stub.getFullName()))
                .findFirst()
                .orElseThrow();
            assertTrue(math.methods().stream().anyMatch(method -> method.name().equals("clamp")));
        }
    }
}
