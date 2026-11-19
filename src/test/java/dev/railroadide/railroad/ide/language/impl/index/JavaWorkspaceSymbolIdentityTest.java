package dev.railroadide.railroad.ide.language.impl.index;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.java.api.JavaSymbolIds;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JavaWorkspaceSymbolIdentityTest {
    @Test
    public void capturedWildcardSignaturesUseDiscoveredProjectTypesAndKeepDocumentRevision() {
        var store = new DefaultDocumentStore("symbols", new WorkspaceConfigurationSnapshot(0, Set.of()));
        var dependency = source("Widget.java", "package dependency; public class Widget {}");
        var use = source("Use.java", "import dependency.*; class Use { void accept(Widget value) {} }");
        store.publishBacking(dependency);
        store.publishBacking(use);
        try (var provider = new JavaWorkspaceAnalysisProvider(() -> true, (_, _) -> JavaProjectSemanticIndex.empty())) {
            var workspace = store.capture();
            var analysis = provider.capture(workspace);
            var id = JavaSymbolIds.method(JavaSymbolIds.type("Use"), "accept", List.of("dependency.Widget"));
            var shard = analysis.graph().shards().get(use.id());
            assertNotNull(shard);
            assertEquals(use.version(), shard.documentVersion());
            assertTrue(shard.declarations().stream().anyMatch(symbol -> symbol.id().equals(id)));
            assertSame(analysis, provider.capture(workspace));
            assertTrue(analysis.resolvedGraph().shards().get(use.id()).declarations().stream()
                .anyMatch(symbol -> symbol.id().equals(id)));
        }
    }

    private static TextDocumentSnapshot source(String name, String text) {
        return new TextDocumentSnapshot(DocumentId.create(), DocumentUri.virtual("memory", name),
            DocumentVersion.initial().next(), "java", text, StandardCharsets.UTF_8);
    }
}
