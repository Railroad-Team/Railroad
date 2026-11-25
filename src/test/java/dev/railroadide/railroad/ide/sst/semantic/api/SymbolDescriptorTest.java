package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSyntaxParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SymbolDescriptorTest {
    @Test
    public void validatesProviderOwnedValueIdentities() {
        assertEquals(new SymbolId("custom:one"), new SymbolId("custom:one"));
        assertEquals(new SymbolKindId("custom:widget"), SymbolKindId.from(() -> "custom:widget"));
        assertThrows(IllegalArgumentException.class, () -> new SymbolId("missingNamespace"));
        assertThrows(IllegalArgumentException.class, () -> new SymbolId("custom:bad value"));
        assertThrows(IllegalArgumentException.class, () -> new SymbolKindId("custom:"));
    }

    @Test
    public void explicitIdentityDoesNotShareAttachmentsAcrossSnapshots() {
        var firstTree = JavaSyntaxParser.parse("class Example {}");
        var secondTree = JavaSyntaxParser.parse("/* another revision */ class Example {}");
        var id = new SymbolId("test:Example");
        var kind = new SymbolKindId("test:type");
        var first = new SimpleSymbol(id, kind, "Example", "Example", firstTree.root());
        var second = new SimpleSymbol(id, kind, "Example", "Example", secondTree.root());
        assertEquals(first.id(), second.id());
        assertNotSame(first, second);
        assertSame(firstTree.root(), first.declaration().orElseThrow());
        assertSame(secondTree.root(), second.declaration().orElseThrow());
        assertNotSame(first.declaration().orElseThrow(), second.declaration().orElseThrow());
        assertTrue(new SimpleSymbol(kind, "local", null, firstTree.root()).id().isEmpty());
        assertTrue(new SimpleSymbol(id, kind, "Example", "Example", null).declaration().isEmpty());
    }

    @Test
    public void copiesEveryCollectionAtPublication() {
        List<TypeReference> arguments = new ArrayList<>(List.of(new TypeReference("Element")));
        var type = new TypeReference("List", arguments, 1, new SymbolId("test:List"));
        List<SymbolSignature.Parameter> parameters = new ArrayList<>(
            List.of(new SymbolSignature.Parameter("values", type, true)));
        var signature = new SymbolSignature(List.of(), parameters, new TypeReference("void"), List.of());
        Set<String> modifiers = new HashSet<>(Set.of("static"));
        Map<String, String> extensions = new HashMap<>(Map.of("test:property", "value"));
        var metadata = new SymbolMetadata(modifiers, "public", true, List.of(new TypeReference("Deprecated")),
            extensions);
        List<SymbolLocation> locations = new ArrayList<SymbolLocation>();
        var descriptor = new SymbolDescriptor(new SymbolId("test:method"), new SymbolKindId("test:callable"),
            "run", null, null, signature, type, metadata, locations);
        arguments.clear();
        parameters.clear();
        modifiers.clear();
        extensions.clear();
        locations.add(new SymbolLocation(DocumentUri.virtual("test", "generated"),
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED), null, null, true));

        assertEquals("List<Element>[]", descriptor.declaredType().displayName());
        assertEquals(1, descriptor.signature().parameters().size());
        assertEquals(Set.of("static"), descriptor.metadata().modifiers());
        assertEquals("value", descriptor.metadata().extensions().get("test:property"));
        assertTrue(descriptor.locations().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.signature().parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.metadata().annotations().clear());
    }

    @Test
    public void rejectsSelectionsOutsideTheirDeclarationRevision() {
        DocumentId document = DocumentId.create();
        var version = new DocumentVersion(1);
        var declaration = new Location.TextLocation(document, version, new TextRange(0, 20), 0, 0);
        var selection = new Location.TextLocation(document, version, new TextRange(6, 10), 0, 6);
        var uri = DocumentUri.virtual("memory", "Example.java");
        var origin = DocumentOrigin.original(uri);
        assertDoesNotThrow(() -> new SymbolLocation(uri, origin, declaration, selection, true));
        var stale = new Location.TextLocation(document, new DocumentVersion(2), new TextRange(6, 10), 0, 6);
        assertThrows(IllegalArgumentException.class, () -> new SymbolLocation(uri, origin, declaration, stale, true));
        var outside = new Location.TextLocation(document, version, new TextRange(15, 21), 0, 15);
        assertThrows(IllegalArgumentException.class, () -> new SymbolLocation(uri, origin, declaration, outside, true));
        assertThrows(IllegalArgumentException.class, () -> new SymbolLocation(uri, origin, null, selection, true));
    }

    @Test
    public void originsAndAlternateViewsDoNotChangeIdentity() {
        var id = new SymbolId("test:declaration");
        var source = new SymbolLocation(DocumentUri.virtual("memory", "Example.java"),
            new DocumentOrigin(BackingKind.MEMORY, ProvenanceKind.ORIGINAL), null, null, true);
        var decompiled = new SymbolLocation(DocumentUri.virtual("decompiled", "Example.java"),
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.DECOMPILED), null, null, true);
        var symbol = new SymbolDescriptor(id, new SymbolKindId("test:type"), "Example", "Example", null,
            null, null, SymbolMetadata.EMPTY, List.of(source, decompiled));
        assertEquals(id, symbol.id());
        assertEquals(2, symbol.locations().size());
    }

    @Test
    public void rejectsInvalidSignaturesAndSelfOwnership() {
        var parameter = new SymbolSignature.Parameter("items", new TypeReference("int"), true);
        assertThrows(IllegalArgumentException.class,
            () -> new SymbolSignature(List.of(), List.of(parameter, parameter), null, List.of()));
        var id = new SymbolId("test:self");
        assertThrows(IllegalArgumentException.class, () -> new SymbolDescriptor(id, new SymbolKindId("test:type"),
            "Self", null, id, null, null, SymbolMetadata.EMPTY, List.of()));
    }
}
