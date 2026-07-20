package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentIdentityRegistryTest {
    @TempDir
    Path tempDirectory;

    @Test
    void normalizedAndAbsoluteSpellingsShareOneIdentity() throws IOException {
        Path nested = Files.createDirectories(tempDirectory.resolve("nested"));
        Path file = Files.writeString(tempDirectory.resolve("Example.java"), "class Example {}");
        Path alternateSpelling = nested.resolve("..").resolve(file.getFileName());
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();

        DocumentId first = registry.getOrCreate(file);
        DocumentId second = registry.getOrCreate(alternateSpelling);

        assertEquals(first, second);
        assertEquals(first, registry.find(file.toAbsolutePath()).orElseThrow());
    }

    @Test
    void physicalAliasesShareOneIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("source.bin"), "content");
        Path hardLink = tempDirectory.resolve("alias.bin");
        try {
            Files.createLink(hardLink, file);
        } catch (UnsupportedOperationException | SecurityException | IOException ignored) {
            org.junit.jupiter.api.Assumptions.abort("Hard links are unavailable on this filesystem");
        }

        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        assertEquals(registry.getOrCreate(file), registry.getOrCreate(hardLink));
    }

    @Test
    void pathlessDocumentsReceiveIndependentIdentities() {
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();

        DocumentId generatedText = registry.create();
        DocumentId inMemoryBinary = registry.create();

        assertNotEquals(generatedText, inMemoryBinary);
    }

    @Test
    void explicitAssociationSupportsPathlessDocumentsThatGainALocation() throws IOException {
        Path generatedOutput = Files.writeString(tempDirectory.resolve("Generated.java"), "class Generated {}");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.create();

        registry.associate(documentId, generatedOutput);

        assertEquals(documentId, registry.getOrCreate(generatedOutput));
    }

    @Test
    void fileUriAndPathResolveToTheSameIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Physical.java"), "class Physical {}");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();

        DocumentId fromPath = registry.getOrCreate(file);
        DocumentId fromUri = registry.getOrCreate(DocumentUri.fromPath(file));

        assertEquals(fromPath, fromUri);
    }

    @Test
    void virtualUriResolutionIsStableAndIndependent() {
        DocumentUri generated = DocumentUri.virtual("generated", "demo/Generated.java");
        DocumentUri inMemory = DocumentUri.virtual("memory", "demo/Generated.java");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();

        DocumentId generatedId = registry.getOrCreate(generated);

        assertEquals(generatedId, registry.getOrCreate(generated));
        assertNotEquals(generatedId, registry.getOrCreate(inMemory));
    }

    @Test
    void rebindPreservesIdentityAcrossVirtualUriChanges() {
        DocumentUri previous = DocumentUri.virtual("generated", "first/Generated.java");
        DocumentUri current = DocumentUri.virtual("generated", "second/Generated.java");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.getOrCreate(previous);

        registry.rebind(documentId, previous, current);

        assertTrue(registry.find(previous).isEmpty());
        assertEquals(documentId, registry.find(current).orElseThrow());
    }

    @Test
    void rebindPreservesIdentityAcrossRename() throws IOException {
        Path previous = Files.writeString(tempDirectory.resolve("Before.java"), "class Before {}");
        Path renamed = tempDirectory.resolve("After.java");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.getOrCreate(previous);

        Files.move(previous, renamed);
        registry.rebind(documentId, previous, renamed);

        assertFalse(registry.find(previous).isPresent());
        assertEquals(documentId, registry.find(renamed).orElseThrow());
    }

    @Test
    void conflictingAssociationIsRejected() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Owned.java"), "class Owned {}");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        registry.getOrCreate(file);

        assertThrows(IllegalStateException.class, () -> registry.associate(registry.create(), file));
    }

    @Test
    void concurrentResolutionReturnsOneIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Concurrent.java"), "class Concurrent {}");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();

        Set<DocumentId> identities = IntStream.range(0, 100)
            .parallel()
            .mapToObj(index -> registry.getOrCreate(file))
            .collect(Collectors.toSet());

        assertEquals(1, identities.size());
    }

    @Test
    void releaseForgetsAssociationsWithoutReusingTheIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Released.java"), "class Released {}");
        DocumentIdentityRegistry registry = new DocumentIdentityRegistry();
        DocumentId released = registry.getOrCreate(file);

        registry.release(released);
        DocumentId replacement = registry.getOrCreate(file);

        assertNotEquals(released, replacement);
    }
}
