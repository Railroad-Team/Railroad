package dev.railroadide.railroad.ide.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

public class BuildProblemsTest {
    @TempDir
    private Path project;

    @Test
    public void chunkedCompilerOutputRetainsLocationsAndConsoleText() throws Exception {
        var problems = new BuildProblems();
        var report = problems.begin("compile", project);
        var console = new StringBuilder();
        String text = "src/With Spaces.java:12:8: error: missing café\r\nsrc/Other.java:9: error: unmatched brace";
        try (var stream = report.output(StandardCharsets.UTF_8, console::append)) {
            for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
                stream.write(value);
            }
        }
        report.location("src/With Spaces.java", 12, 8, "missing café");
        report.finish(new IllegalStateException("Compilation failed"));
        assertEquals(text, console.toString());
        assertEquals(List.of(new BuildProblems.Problem(project.resolve("src/With Spaces.java"), 12, 8, "missing café"),
            new BuildProblems.Problem(project.resolve("src/Other.java"), 9, 1, "unmatched brace")),
            problems.problems());
    }

    @Test
    public void supersededAndCancelledBuildsCannotRestoreOldProblems() {
        var problems = new BuildProblems();
        var older = problems.begin("compile", project);
        var other = problems.begin("other", project);
        var latest = problems.begin("compile", project);
        latest.finish(new IllegalStateException("Dependency resolution failed"));
        older.finish(new IllegalStateException("Stale"));
        assertEquals(List.of(new BuildProblems.Problem(null, 0, 1, "Dependency resolution failed")),
            problems.problems());
        other.finish(new IllegalStateException("Other failure"));
        assertEquals(List.of("Dependency resolution failed", "Other failure"),
            problems.problems().stream().map(BuildProblems.Problem::message).toList());
        var rerun = problems.begin("compile", project);
        assertEquals(List.of("Other failure"),
            problems.problems().stream().map(BuildProblems.Problem::message).toList());
        rerun.finish(new CancellationException("Stopped"));
        assertEquals(List.of("Other failure"),
            problems.problems().stream().map(BuildProblems.Problem::message).toList());
        problems.begin("other", project).finish(null);
        assertTrue(problems.problems().isEmpty());
    }
}
