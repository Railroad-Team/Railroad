package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.BackingKind;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.ProvenanceKind;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdtDiagnosticsProviderTest {
    private static final String INVALID_SOURCE = "public class Example { int value = ; }";

    @BeforeAll
    public static void registerJavaLanguage() {
        if (!LanguageSupportRegistry.contains(JavaLanguageSupport.LANGUAGE_ID)) {
            LanguageSupportRegistry.register(new JavaLanguageSupport());
        }
    }

    @Test
    public void reportsSyntaxErrorsInGeneratedVirtualDocument() {
        var snapshot = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.virtual("generated", "sources/Example.java"),
            DocumentVersion.initial(),
            JavaLanguageSupport.LANGUAGE_ID,
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            INVALID_SOURCE,
            StandardCharsets.UTF_8);

        assertTrue(snapshot.uri().filePath().isEmpty());

        List<TextEditorDiagnostic> diagnostics = assertDoesNotThrow(
            () -> new JdtDiagnosticsProvider().compute(snapshot));

        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.ERROR));
        for (TextEditorDiagnostic diagnostic : diagnostics) {
            assertEquals(snapshot.id(), diagnostic.location().documentId());
            assertEquals(snapshot.version(), diagnostic.location().documentVersion());
            assertDoesNotThrow(() -> diagnostic.location().validateAgainst(snapshot));
        }
    }

    @Test
    public void producesSameDiagnosticsForPhysicalAndVirtualLocations() {
        var physical = new TextDocumentSnapshot(
            DocumentId.create(),
            DocumentUri.fromPath(Path.of("sources", "Example.java")),
            DocumentVersion.initial(),
            JavaLanguageSupport.LANGUAGE_ID,
            INVALID_SOURCE,
            StandardCharsets.UTF_8);
        var virtual = new TextDocumentSnapshot(
            physical.id(),
            DocumentUri.virtual("generated", "sources/Example.java"),
            physical.version(),
            physical.languageId(),
            physical.text(),
            physical.encoding());
        var provider = new JdtDiagnosticsProvider();

        List<TextEditorDiagnostic> physicalDiagnostics = provider.compute(physical);
        List<TextEditorDiagnostic> virtualDiagnostics = provider.compute(virtual);

        assertTrue(physicalDiagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.ERROR));
        assertEquals(physicalDiagnostics.size(), virtualDiagnostics.size());
        for (int index = 0; index < physicalDiagnostics.size(); index++) {
            TextEditorDiagnostic expected = physicalDiagnostics.get(index);
            TextEditorDiagnostic actual = virtualDiagnostics.get(index);
            assertEquals(expected.kind(), actual.kind());
            assertEquals(expected.code(), actual.code());
            assertEquals(expected.message(), actual.message());
            assertEquals(expected.location().range().start, actual.location().range().start);
            assertEquals(expected.location().range().end, actual.location().range().end);
            assertEquals(expected.location().line(), actual.location().line());
            assertEquals(expected.location().column(), actual.location().column());
        }
    }

    @Test
    public void derivesCompilationUnitNameFromOpaqueJarUri() {
        assertEquals("Example.java", JdtDiagnosticsProvider.compilationUnitName(
            DocumentUri.parse("jar:file:///libs/example.jar!/demo/Example.java")));
        assertEquals("Snapshot.java", JdtDiagnosticsProvider.compilationUnitName(
            DocumentUri.parse("jar:file:///libs/example.jar!/demo/")));
    }

    @Test
    public void supportsUrisWithoutACompilationUnitFilename() {
        for (DocumentUri uri : List.of(DocumentUri.parse("generated:example"), DocumentUri.virtual("generated", "/"))) {
            var snapshot = new TextDocumentSnapshot(
                DocumentId.create(),
                uri,
                DocumentVersion.initial(),
                JavaLanguageSupport.LANGUAGE_ID,
                INVALID_SOURCE,
                StandardCharsets.UTF_8);

            List<TextEditorDiagnostic> diagnostics = assertDoesNotThrow(
                () -> new JdtDiagnosticsProvider().compute(snapshot));

            assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.ERROR));
        }
    }
}
