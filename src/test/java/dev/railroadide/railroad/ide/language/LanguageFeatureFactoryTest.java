package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageFeatureFactoryTest {
    @TempDir
    public Path tempDirectory;

    @Test
    public void factoryLambdasReceivePhysicalAndVirtualDocumentUris() {
        Project project = project();
        LanguageFeatureFactory<DocumentUri> factory = (receivedProject, uri) -> {
            assertSame(project, receivedProject);
            return uri;
        };

        for (DocumentUri uri : virtualUris()) {
            assertSame(uri, factory.create(project, uri));
        }

        DocumentUri physical = DocumentUri.fromPath(tempDirectory.resolve("Example.java"));
        assertSame(physical, factory.create(project, physical));
    }

    @Test
    public void builtInJavaFactoriesCreateAllFeaturesForVirtualDocuments() {
        Project project = project();
        var support = new JavaLanguageSupport();

        for (DocumentUri uri : virtualUris()) {
            CodeEditorConfig config = CodeEditorConfig.fromDocument(project, uri, support, null);

            assertEquals(JavaLanguageSupport.LANGUAGE_ID, config.languageId());
            assertNotNull(config.completionProvider());
            assertNotNull(config.diagnosticsProvider());
            assertNotNull(config.signatureHelpProvider());
        }

        CodeEditorConfig physical = CodeEditorConfig.fromLanguageSupport(project,
            tempDirectory.resolve("Example.java"), support, null);
        assertNotNull(physical.completionProvider());
        assertNotNull(physical.diagnosticsProvider());
        assertNotNull(physical.signatureHelpProvider());
    }

    @Test
    public void editorConfigurationPassesDocumentUriToFactoryWithoutOpeningAnEditor() {
        var receivedUri = new AtomicReference<DocumentUri>();
        CompletionProvider completion = (snapshot, trigger) -> CompletionResult.empty();
        var support = new BaseLanguageSupport("test", "Test", Set.of("test")) {
            @Override
            public EditorOpenView open(Project project, Path file) {
                throw new AssertionError("Feature creation must not open an editor");
            }

            @Override
            public LanguageFeatureFactory<CompletionProvider> completionFactory() {
                return (project, uri) -> {
                    receivedUri.set(uri);
                    return completion;
                };
            }
        };
        DocumentUri uri = DocumentUri.virtual("generated", "/demo/Example.test");

        CodeEditorConfig config = CodeEditorConfig.fromDocument(project(), uri, support, null);

        assertSame(uri, receivedUri.get());
        assertSame(completion, config.completionProvider());
        assertNull(config.diagnosticsProvider());
        assertNull(config.signatureHelpProvider());
    }

    @Test
    public void fileBackedEditorConfigurationConvertsPathToDocumentUri() {
        var receivedUri = new AtomicReference<DocumentUri>();
        CompletionProvider completion = (snapshot, trigger) -> CompletionResult.empty();
        var support = new BaseLanguageSupport("test", "Test", Set.of("test")) {
            @Override
            public EditorOpenView open(Project project, Path file) {
                throw new AssertionError("Feature creation must not open an editor");
            }

            @Override
            public LanguageFeatureFactory<CompletionProvider> completionFactory() {
                return (project, uri) -> {
                    receivedUri.set(uri);
                    return completion;
                };
            }
        };
        Project project = project();
        Path file = tempDirectory.resolve("Example.test");

        CodeEditorConfig config = CodeEditorConfig.fromLanguageSupport(project, file, support, null);

        assertSame(completion, config.completionProvider());
        assertEquals(DocumentUri.fromPath(file), receivedUri.get());
    }

    private static List<DocumentUri> virtualUris() {
        return List.of(
            DocumentUri.virtual("generated", "/demo/Example.java"),
            DocumentUri.parse("jar:file:///libs/example.jar!/demo/Example.java"),
            DocumentUri.virtual("decompiled", "/demo/Example.java"),
            DocumentUri.inMemory(DocumentId.create()));
    }

    private Project project() {
        return (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> tempDirectory;
                case "getAlias" -> "Factory test project";
                case "hasFacet" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "FactoryTestProject[" + tempDirectory + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
