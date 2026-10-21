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

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageFeatureFactoryTest {
    @TempDir
    public Path tempDirectory;

    @Test
    public void preservesPathLambdaContractAndBridgesPhysicalDocumentUris() {
        LanguageFeatureFactory<Path> factory = (project, file) -> file;
        Path relative = Path.of("sources", "Example.java");

        assertSame(relative, factory.create(null, relative));
        assertNull(factory.create(null, null));
        assertEquals(relative.toAbsolutePath().normalize(),
            factory.createForDocument(null, DocumentUri.fromPath(relative)));
    }

    @Test
    public void legacyFactoriesRejectVirtualUrisWithoutInventingAPath() {
        var calls = new AtomicInteger();
        LanguageFeatureFactory<Path> factory = (project, file) -> {
            calls.incrementAndGet();
            return file;
        };

        for (DocumentUri uri : virtualUris()) {
            UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> factory.createForDocument(null, uri));
            assertTrue(exception.getMessage().contains(uri.toString()));
            assertTrue(exception.getMessage().contains("fromUri"));
        }
        assertEquals(0, calls.get());
    }

    @Test
    public void uriFactoriesAcceptVirtualUrisAndLegacyPathCallers() {
        Project project = project();
        LanguageFeatureFactory<DocumentUri> factory = LanguageFeatureFactory.fromUri((receivedProject, uri) -> {
            assertSame(project, receivedProject);
            return uri;
        });

        for (DocumentUri uri : virtualUris()) {
            assertSame(uri, factory.createForDocument(project, uri));
        }

        Path relative = Path.of("sources", "Example.java");
        assertEquals(DocumentUri.fromPath(relative), factory.create(project, relative));
        DocumentUri physical = DocumentUri.fromPath(tempDirectory.resolve("Example.java"));
        assertSame(physical, factory.createForDocument(project, physical));
    }

    @Test
    public void rejectsNullUriAndCreationFunction() {
        assertThrows(NullPointerException.class, () -> LanguageFeatureFactory.fromUri(null));
        LanguageFeatureFactory<Path> legacy = (project, file) -> file;
        LanguageFeatureFactory<DocumentUri> nativeFactory = LanguageFeatureFactory.fromUri((project, uri) -> uri);

        assertThrows(NullPointerException.class, () -> legacy.createForDocument(null, null));
        assertThrows(NullPointerException.class, () -> nativeFactory.createForDocument(null, null));
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
                return LanguageFeatureFactory.fromUri((project, uri) -> {
                    receivedUri.set(uri);
                    return completion;
                });
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
    public void editorConfigurationBridgesLegacyFactoriesForFilesAndRejectsVirtualDocuments() {
        var receivedPath = new AtomicReference<Path>();
        CompletionProvider completion = (snapshot, trigger) -> CompletionResult.empty();
        var support = new BaseLanguageSupport("test", "Test", Set.of("test")) {
            @Override
            public EditorOpenView open(Project project, Path file) {
                throw new AssertionError("Feature creation must not open an editor");
            }

            @Override
            public LanguageFeatureFactory<CompletionProvider> completionFactory() {
                return (project, file) -> {
                    receivedPath.set(file);
                    return completion;
                };
            }
        };
        Project project = project();
        Path file = tempDirectory.resolve("Example.test");

        CodeEditorConfig config = CodeEditorConfig.fromLanguageSupport(project, file, support, null);

        assertSame(completion, config.completionProvider());
        assertEquals(file.toAbsolutePath().normalize(), receivedPath.get());
        receivedPath.set(null);
        assertThrows(UnsupportedOperationException.class, () -> CodeEditorConfig.fromDocument(project,
            DocumentUri.virtual("generated", "/demo/Example.test"), support, null));
        assertNull(receivedPath.get());
    }

    @Test
    public void loadsFactoryCompiledAgainstTheOriginalPathOnlyInterface() throws Exception {
        Path sources = Files.createDirectory(tempDirectory.resolve("legacy-sources"));
        Path classes = Files.createDirectory(tempDirectory.resolve("legacy-classes"));
        Path oldInterface = sources.resolve("LanguageFeatureFactory.java");
        Files.writeString(oldInterface, """
            package dev.railroadide.railroad.ide.language;
            import dev.railroadide.railroad.plugin.spi.dto.Project;
            import java.nio.file.Path;
            @FunctionalInterface
            public interface LanguageFeatureFactory<T> {
                T create(Project project, Path file);
            }
            """);
        Path projectType = sources.resolve("Project.java");
        Files.writeString(projectType, """
            package dev.railroadide.railroad.plugin.spi.dto;
            public interface Project {}
            """);
        Path implementation = sources.resolve("LegacyFactory.java");
        Files.writeString(implementation, """
            package legacy;
            import dev.railroadide.railroad.ide.language.LanguageFeatureFactory;
            import dev.railroadide.railroad.plugin.spi.dto.Project;
            import java.nio.file.Path;
            public final class LegacyFactory implements LanguageFeatureFactory<String> {
                public String create(Project project, Path file) { return file.toString(); }
            }
            """);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var compilerOutput = new ByteArrayOutputStream();
        assertEquals(0, compiler.run(null, compilerOutput, compilerOutput, "-proc:none", "-d", classes.toString(),
            oldInterface.toString(), projectType.toString(), implementation.toString()),
            compilerOutput.toString(StandardCharsets.UTF_8));

        // Parent-first loading resolves the old implementation against the current interface and Project type.
        try (var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()},
            LanguageFeatureFactory.class.getClassLoader())) {
            Object instance = loader.loadClass("legacy.LegacyFactory").getConstructor().newInstance();
            assertInstanceOf(LanguageFeatureFactory.class, instance);
            @SuppressWarnings("unchecked")
            var factory = (LanguageFeatureFactory<String>) instance;
            Path file = tempDirectory.resolve("Example.java");

            assertEquals(file.toString(), factory.create(null, file));
            assertEquals(file.toString(), factory.createForDocument(null, DocumentUri.fromPath(file)));
            assertThrows(UnsupportedOperationException.class,
                () -> factory.createForDocument(null, DocumentUri.virtual("generated", "/demo/Example.java")));
        }
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
