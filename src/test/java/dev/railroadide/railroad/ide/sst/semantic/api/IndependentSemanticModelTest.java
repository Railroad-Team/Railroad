package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.BinaryDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.DocumentVersion;
import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class IndependentSemanticModelTest {
    @Test
    public void languageDefinedKindsAndTypesPublishWithoutJavaTaxonomies() {
        DocumentId id = DocumentId.create();
        var snapshot = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "example:config", "name", StandardCharsets.UTF_8);
        var node = new ConfigToken("name");
        var tree = new SyntaxTree(snapshot, node);
        var scope = Scope.root();
        SymbolKind keyKind = () -> "example:config-key";
        Type.Kind scalarKind = () -> "example:scalar";
        var symbol = new SimpleSymbol(keyKind, "name", null, node);
        var type = new ConfigType(scalarKind, "scalar");
        scope.declare(symbol);
        SemanticModel model = SemanticModel.builder(tree, scope).declare(node, symbol).type(node, type).build();

        assertSame(snapshot, model.syntaxTree().documentSnapshot());
        assertSame(keyKind, model.declaredSymbol(node).orElseThrow().kind());
        assertSame(type, model.inferredType(node).orElseThrow());
        assertEquals("example:scalar", model.inferredType(node).orElseThrow().kind().id());
        assertThrows(IllegalStateException.class, () -> scope.declare(symbol));
    }

    @Test
    public void binarySymbolsAndLocationsNeedNoFabricatedSyntax() {
        DocumentId id = DocumentId.create();
        var snapshot = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "example:packet", new byte[]{1, 2, 3});
        var symbol = new SimpleSymbol(() -> "example:packet-field", "header", null, null);
        var location = Location.BinaryLocation.from(snapshot, 0, 2);

        assertTrue(symbol.declaration().isEmpty());
        assertEquals("example:packet-field", symbol.kind().id());
        assertDoesNotThrow(() -> location.validateAgainst(snapshot));
        assertEquals(2, location.range().content(snapshot).remaining());
    }

    private record ConfigType(Type.Kind kind, String displayName) implements Type {
    }

    private record ConfigToken(String text) implements SyntaxToken {
        @Override
        public SyntaxKind kind() {
            return SyntaxKind.of("example:config-key");
        }

        @Override
        public int start() {
            return 0;
        }

        @Override
        public int end() {
            return text.length();
        }

        @Override
        public Optional<SyntaxNode> parent() {
            return Optional.empty();
        }
    }
}
