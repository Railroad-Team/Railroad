package dev.railroadide.railroad.debug.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectSourceResolverTest {
    @TempDir
    private Path directory;

    @Test
    public void discoversMainTestAndSubprojectSourceSetsButSkipsBuildCopies() throws Exception {
        String[] included = {"src/main/java", "src/test/java", "module/src/main/java",
            "module/src/integrationTest/java"};
        for (String root : included) {
            Files.createDirectories(directory.resolve(root));
        }
        Files.createDirectories(directory.resolve("build/copy/src/main/java"));
        var resolver = ProjectSourceResolver.create(directory);
        for (String root : included) {
            assertEquals("demo.Main*", resolver.classPatternFor(new DebugSource.FileSource(
                directory.resolve(root).resolve("demo/Main.java"))).orElseThrow());
        }
        assertTrue(resolver.classPatternFor(new DebugSource.FileSource(
            directory.resolve("build/copy/src/main/java/demo/Main.java"))).isEmpty());
    }
}
