package dev.railroadide.railroad.ide.indexing;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.stream.Collectors;
import dev.railroadide.railroad.ide.classparser.stub.ClassStub;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndexesTest {
    @Test
    public void standardLibraryScanIncludesClassesOutsideJavaBase() {
        List<ClassStub> stubs = Indexes.scanStandardLibrary();
        Set<String> qualifiedNames = stubs.stream()
            .map(stub -> stub.getFullName())
            .collect(Collectors.toSet());

        assertTrue(qualifiedNames.contains("java.util.List"));
        assertTrue(qualifiedNames.contains("java.sql.Connection"));
        assertTrue(qualifiedNames.contains("java.net.http.HttpClient"));

        boolean runtimeHasClamp = Arrays.stream(Math.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("clamp"));
        if (runtimeHasClamp) {
            ClassStub math = stubs.stream()
                .filter(stub -> "java.lang.Math".equals(stub.getFullName()))
                .findFirst()
                .orElseThrow();
            assertTrue(math.methods().stream().anyMatch(method -> method.name().equals("clamp")));
        }
    }
}
