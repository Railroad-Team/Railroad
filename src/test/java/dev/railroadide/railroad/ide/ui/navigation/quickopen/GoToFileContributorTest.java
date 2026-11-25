package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class GoToFileContributorTest {
    @TempDir
    public Path root;

    @Test
    public void scanListsFilesBySlashSeparatedRelativePath() throws IOException {
        write("build.gradle");
        write("src/main/java/dev/Main.java");
        write(".editorconfig");

        assertEquals(List.of(".editorconfig", "build.gradle", "src/main/java/dev/Main.java"), relativePaths());
    }

    @Test
    public void scanSkipsHiddenDirectoriesAndBuildOutputs() throws IOException {
        write(".git/config");
        write(".gradle/cache.bin");
        write("build/classes/Main.class");
        write("module/out/Main.class");
        write("node_modules/lib/index.js");
        write("neoforge/bin/main/META-INF/services/dev.Service");
        write("module/src/Kept.java");

        assertEquals(List.of("module/src/Kept.java"), relativePaths());
    }

    @Test
    public void buildOutputNamesInsideSourcesAreKept() throws IOException {
        write("src/main/java/dev/build/Builder.java");
        write("src/main/java/dev/bin/Binary.java");
        write("src/main/resources/out/template.txt");

        assertEquals(List.of("src/main/java/dev/bin/Binary.java", "src/main/java/dev/build/Builder.java",
            "src/main/resources/out/template.txt"), relativePaths());
    }

    @Test
    public void scanStopsWhenCancelled() throws IOException {
        write("src/Main.java");

        assertThrows(CancellationException.class, () -> GoToFileContributor.scan(root, () -> {
            throw new CancellationException();
        }));
    }

    @Test
    public void entriesShowTheirNameAndContainingDirectory() {
        var contributor = new GoToFileContributor();
        var nested = new GoToFileContributor.FileEntry(root.resolve("src/Main.java"), "src/Main.java");
        var top = new GoToFileContributor.FileEntry(root.resolve("README.md"), "README.md");

        assertEquals("Main.java", contributor.name(nested));
        assertEquals("src", contributor.detail(nested));
        assertEquals("", contributor.detail(top));
        assertEquals("src/Main.java", contributor.normalizePattern("src\\Main.java"));
    }

    private List<String> relativePaths() throws IOException {
        return GoToFileContributor.scan(root, () -> {
        }).stream().map(GoToFileContributor.FileEntry::relativePath).sorted().toList();
    }

    private void write(String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
    }
}
