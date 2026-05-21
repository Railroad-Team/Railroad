package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTreeDumper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaSyntaxTreeDumpTest {
    private static final String EMPTY_SNAPSHOT_RESOURCE =
            "/dev/railroadide/railroad/ide/sst/impl/java/fixtures/tree-dump/empty.snapshot.txt";

    @Test
    void dumpsEmptyCompilationUnitUsingSnapshot() throws IOException {
        String actual = SyntaxTreeDumper.dump(JavaSyntaxParser.parse(""));
        String expected;
        try (var stream = JavaSyntaxTreeDumpTest.class.getResourceAsStream(EMPTY_SNAPSHOT_RESOURCE)) {
            expected = new String(
                    Objects.requireNonNull(stream, "Missing fixture resource: " + EMPTY_SNAPSHOT_RESOURCE).readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        expected = expected.replace("\r\n", "\n").replace('\r', '\n');
        assertEquals(expected, actual);
    }
}
