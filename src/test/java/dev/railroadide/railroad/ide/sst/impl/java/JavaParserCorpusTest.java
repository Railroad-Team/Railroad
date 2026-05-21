package dev.railroadide.railroad.ide.sst.impl.java;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaParserCorpusTest {
    private static final String CORPUS_RESOURCE_ROOT = "/dev/railroadide/railroad/ide/sst/impl/java/corpus";
    private static final String MANIFEST_FILENAME = "manifest.csv";
    private static final String MANIFEST_HEADER = "relative_path,kind,features";

    @TestFactory
    List<DynamicTest> validCorpusSyntaxRoundTrips() throws IOException {
        return manifestEntries().stream()
                .filter(entry -> entry.kind().equals("valid"))
                .map(entry -> DynamicTest.dynamicTest("syntax roundtrip: " + entry.relativePath(), () -> {
                    String source = readCorpusSource(entry);
                    JavaSyntaxAssertions.assertRoundTrip(source);
                }))
                .toList();
    }

    @TestFactory
    List<DynamicTest> validCorpusHasNoSyntaxRecoveryArtifacts() throws IOException {
        return manifestEntries().stream()
                .filter(entry -> entry.kind().equals("valid"))
                .map(entry -> DynamicTest.dynamicTest("syntax strict: " + entry.relativePath(), () -> {
                    String source = readCorpusSource(entry);
                    JavaSyntaxAssertions.assertParsesWithoutRecovery(source);
                }))
                .toList();
    }

    @TestFactory
    List<DynamicTest> recoveryCorpusProducesRecoveryMarkersAndSyntaxTrees() throws IOException {
        return manifestEntries().stream()
                .filter(entry -> entry.kind().equals("recovery"))
                .map(entry -> DynamicTest.dynamicTest("recovery: " + entry.relativePath(), () -> {
                    String source = readCorpusSource(entry);
                    JavaSyntaxAssertions.assertParsesWithRecovery(source);
                }))
                .toList();
    }

    private static List<CorpusEntry> manifestEntries() throws IOException {
        Path manifestPath = corpusRoot().resolve(MANIFEST_FILENAME);
        assertTrue(Files.isRegularFile(manifestPath), () -> "Missing corpus manifest: " + manifestPath);

        List<String> lines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), "Corpus manifest is empty: " + manifestPath);
        assertEquals(MANIFEST_HEADER, lines.get(0).trim(), "Unexpected corpus manifest header");

        List<CorpusEntry> entries = lines.stream()
                .skip(1)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("#"))
                .map(JavaParserCorpusTest::parseManifestLine)
                .sorted(Comparator.comparing(CorpusEntry::relativePath))
                .toList();
        assertFalse(entries.isEmpty(), "Corpus manifest has no entries: " + manifestPath);
        return List.copyOf(entries);
    }

    private static CorpusEntry parseManifestLine(String line) {
        String[] parts = line.split(",", 3);
        if (parts.length != 3)
            throw new IllegalStateException("Invalid manifest entry: " + line);

        String relativePath = parts[0].trim();
        String kind = parts[1].trim();
        String features = parts[2].trim();
        if (!"valid".equals(kind) && !"recovery".equals(kind))
            throw new IllegalStateException("Unknown corpus kind: " + kind + " in " + line);

        return new CorpusEntry(relativePath, kind, features);
    }

    private static String readCorpusSource(CorpusEntry entry) throws IOException {
        Path path = corpusRoot().resolve(entry.relativePath());
        assertTrue(Files.isRegularFile(path), () -> "Missing corpus file: " + entry.relativePath());
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertFalse(source.isBlank(), () -> "Corpus file is empty: " + entry.relativePath());
        return source;
    }

    private record CorpusEntry(
            String relativePath,
            String kind,
            String features
    ) {
    }

    private static Path corpusRoot() {
        URL url = Objects.requireNonNull(
                JavaParserCorpusTest.class.getResource(CORPUS_RESOURCE_ROOT),
                "Missing parser corpus resources at " + CORPUS_RESOURCE_ROOT
        );
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid corpus resource URI: " + url, exception);
        } catch (IllegalArgumentException exception) {
            throw new UncheckedIOException(new IOException("Unable to resolve corpus resource path: " + url, exception));
        }
    }
}
