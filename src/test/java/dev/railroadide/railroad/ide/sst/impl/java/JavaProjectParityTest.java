package dev.railroadide.railroad.ide.sst.impl.java;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaProjectParityTest {
    private static final int MAX_REPORTED_MISMATCHES = 25;
    private static final Duration MAX_PARSE_TIME_PER_FILE = Duration.ofSeconds(180);

    @Test
    void syntaxParserIsStableAcrossSstProjectSources() throws IOException {
        List<Path> sourceFiles = JavaParserParitySupport.collectProjectJavaSources().stream()
                .filter(JavaProjectParityTest::isSstSource)
                .toList();
        assertFalse(sourceFiles.isEmpty(), "Expected Java sources under src/main/java or src/test/java in /ide/sst/");

        List<String> mismatches = new ArrayList<>();
        for (Path sourceFile : sourceFiles) {
            String source = JavaParserParitySupport.readSource(sourceFile);
            JavaParserParitySupport.ParityResult result = assertTimeoutPreemptively(
                    MAX_PARSE_TIME_PER_FILE,
                    () -> JavaParserParitySupport.analyzeSyntaxOnly(source),
                    () -> "Timed out while parsing " + sourceFile
            );

            List<String> issues = JavaParserParitySupport.syntaxOnlyIssues(result);
            if (!issues.isEmpty()) {
                mismatches.add(JavaParserParitySupport.formatIssues(sourceFile, issues, result));
            }
        }

        assertTrue(mismatches.isEmpty(), () -> buildMismatchMessage(sourceFiles.size(), mismatches));
    }

    private static String buildMismatchMessage(int sourceCount, List<String> mismatches) {
        int shownCount = Math.min(MAX_REPORTED_MISMATCHES, mismatches.size());
        StringBuilder message = new StringBuilder();
        message.append("Found parser parity mismatches in ")
                .append(mismatches.size())
                .append(" / ")
                .append(sourceCount)
                .append(" source files.")
                .append('\n');
        for (int index = 0; index < shownCount; index++) {
            message.append('\n')
                    .append("Mismatch ")
                    .append(index + 1)
                    .append(':')
                    .append('\n')
                    .append(mismatches.get(index))
                    .append('\n');
        }

        if (mismatches.size() > shownCount) {
            message.append('\n')
                    .append("... ")
                    .append(mismatches.size() - shownCount)
                    .append(" more mismatches omitted");
        }

        return message.toString().trim();
    }

    private static boolean isSstSource(Path sourcePath) {
        String normalized = normalizePath(sourcePath);
        return normalized.contains("/ide/sst/");
    }

    private static String normalizePath(Path sourcePath) {
        return sourcePath.toString().replace('\\', '/');
    }
}
