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
        var registry = new DocumentIdentityRegistry();

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
        } catch (UnsupportedOperationException | SecurityException | IOException _) {
            org.junit.jupiter.api.Assumptions.abort("Hard links are unavailable on this filesystem");
        }

        var registry = new DocumentIdentityRegistry();
        assertEquals(registry.getOrCreate(file), registry.getOrCreate(hardLink));
    }

    @Test
    void pathlessDocumentsReceiveIndependentIdentities() {
        var registry = new DocumentIdentityRegistry();

        DocumentId generatedText = registry.create();
        DocumentId inMemoryBinary = registry.create();

        assertNotEquals(generatedText, inMemoryBinary);
        assertEquals(DocumentVersion.initial(), registry.currentVersion(generatedText));
        assertEquals(DocumentVersion.initial(), registry.currentVersion(inMemoryBinary));
    }

    @Test
    void versionsAdvanceAtomicallyForOneDocument() {
        var registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.create();

        Set<DocumentVersion> allocated = IntStream.range(0, 100)
            .parallel()
            .mapToObj(index -> registry.advanceVersion(documentId))
            .collect(Collectors.toSet());

        assertEquals(100, allocated.size());
        assertEquals(new DocumentVersion(100), registry.currentVersion(documentId));
    }

    @Test
    void persistedVersionsCanOnlyBeRestoredForward() {
        var registry = new DocumentIdentityRegistry();
        DocumentId documentId = DocumentId.create();

        registry.restoreVersion(documentId, new DocumentVersion(10));
        registry.restoreVersion(documentId, new DocumentVersion(12));

        assertEquals(new DocumentVersion(12), registry.currentVersion(documentId));
        assertThrows(
            IllegalArgumentException.class,
            () -> registry.restoreVersion(documentId, new DocumentVersion(11)));
    }

    @Test
    void explicitAssociationSupportsPathlessDocumentsThatGainALocation() throws IOException {
        Path generatedOutput = Files.writeString(tempDirectory.resolve("Generated.java"), "class Generated {}");
        var registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.create();

        registry.associate(documentId, generatedOutput);

        assertEquals(documentId, registry.getOrCreate(generatedOutput));
    }

    @Test
    void fileUriAndPathResolveToTheSameIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Physical.java"), "class Physical {}");
        var registry = new DocumentIdentityRegistry();

        DocumentId fromPath = registry.getOrCreate(file);
        DocumentId fromUri = registry.getOrCreate(DocumentUri.fromPath(file));

        assertEquals(fromPath, fromUri);
    }

    @Test
    void virtualUriResolutionIsStableAndIndependent() {
        DocumentUri generated = DocumentUri.virtual("generated", "demo/Generated.java");
        DocumentUri inMemory = DocumentUri.virtual("memory", "demo/Generated.java");
        var registry = new DocumentIdentityRegistry();

        DocumentId generatedId = registry.getOrCreate(generated);

        assertEquals(generatedId, registry.getOrCreate(generated));
        assertNotEquals(generatedId, registry.getOrCreate(inMemory));
    }

    @Test
    void identityRetainsItsLogicalIdWhenItsCurrentUriChanges() {
        var registry = new DocumentIdentityRegistry();
        DocumentIdentity identity = registry.identify(DocumentUri.virtual("memory", "before"));
        DocumentUri newUri = DocumentUri.virtual("memory", "after");

        registry.rebind(identity.id(), identity.uri(), newUri);
        DocumentIdentity rebound = registry.findIdentity(newUri).orElseThrow();

        assertEquals(identity.id(), rebound.id());
        assertEquals(newUri, rebound.uri());
        assertEquals(rebound, identity.at(newUri));
    }

    @Test
    void rebindPreservesIdentityAcrossVirtualUriChanges() {
        DocumentUri previous = DocumentUri.virtual("generated", "first/Generated.java");
        DocumentUri current = DocumentUri.virtual("generated", "second/Generated.java");
        var registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.getOrCreate(previous);
        DocumentVersion version = registry.advanceVersion(documentId);

        registry.rebind(documentId, previous, current);

        assertTrue(registry.find(previous).isEmpty());
        assertEquals(documentId, registry.find(current).orElseThrow());
        assertEquals(version, registry.currentVersion(documentId));
    }

    @Test
    void rebindPreservesIdentityAcrossRename() throws IOException {
        Path previous = Files.writeString(tempDirectory.resolve("Before.java"), "class Before {}");
        Path renamed = tempDirectory.resolve("After.java");
        var registry = new DocumentIdentityRegistry();
        DocumentId documentId = registry.getOrCreate(previous);

        Files.move(previous, renamed);
        registry.rebind(documentId, previous, renamed);

        assertFalse(registry.find(previous).isPresent());
        assertEquals(documentId, registry.find(renamed).orElseThrow());
    }

    @Test
    void conflictingAssociationIsRejected() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Owned.java"), "class Owned {}");
        var registry = new DocumentIdentityRegistry();
        registry.getOrCreate(file);

        assertThrows(IllegalStateException.class, () -> registry.associate(registry.create(), file));
    }

    @Test
    void concurrentResolutionReturnsOneIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Concurrent.java"), "class Concurrent {}");
        var registry = new DocumentIdentityRegistry();

        Set<DocumentId> identities = IntStream.range(0, 100)
            .parallel()
            .mapToObj(index -> registry.getOrCreate(file))
            .collect(Collectors.toSet());

        assertEquals(1, identities.size());
    }

    @Test
    void releaseForgetsAssociationsWithoutReusingTheIdentity() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("Released.java"), "class Released {}");
        var registry = new DocumentIdentityRegistry();
        DocumentId released = registry.getOrCreate(file);

        registry.release(released);
        DocumentId replacement = registry.getOrCreate(file);

        assertNotEquals(released, replacement);
        assertThrows(IllegalStateException.class, () -> registry.currentVersion(released));
        assertThrows(
            IllegalStateException.class,
            () -> registry.associate(released, DocumentUri.virtual("memory", "released")));
        assertThrows(
            IllegalStateException.class,
            () -> registry.restoreVersion(released, new DocumentVersion(100)));
    }
}
