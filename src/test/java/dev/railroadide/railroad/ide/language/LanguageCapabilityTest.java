package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.completion.CompletionProvider;
import dev.railroadide.railroad.ide.completion.CompletionResult;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.ide.ui.codeeditor.CodeEditorConfig;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.sst.document.api.WorkspaceConfigurationSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

public class LanguageCapabilityTest {
    @TempDir
    public Path tempDirectory;

    @Test
    public void disposingJavaLanguageSessionsDoesNotCloseTheProjectOwnedArtifactCache() {
        Project project = project();
        var indexes = Services.PROJECT_LANGUAGE_INDEX_SERVICE;
        var analysis = JavaLanguageSupport.analysisContextProvider();
        var projectOwner = indexes.openProject(project);
        var configuration = new WorkspaceConfigurationSnapshot(1, Set.of("test"),
            Map.of("module.test.jdkHome", tempDirectory.toString()));
        analysis.adoptConfiguration(projectOwner, analysis.prepareConfiguration(projectOwner, configuration));
        int expectedCaches = analysis.cachedSessions();
        var support = new JavaLanguageSupport();
        try (var runtime = new LanguageRuntime()) {
            runtime.register("test-owner", support);
            var firstDialect = runtime.session(project, support.id(), "first");
            var secondDialect = runtime.session(project, support.id(), "second");
            firstDialect.close();
            assertEquals(expectedCaches, analysis.cachedSessions());
            assertFalse(secondDialect.isClosed());

            var context = new LanguageSessionContext(tempDirectory, "second",
                CapabilityRegistry.builder().add(LanguageCapabilities.PROJECT, project).build());
            try (var discardedCandidate = support.openSession(context)) {
                assertFalse(discardedCandidate.isClosed());
            }
            assertEquals(expectedCaches, analysis.cachedSessions());

            analysis.closeSession(projectOwner);
            indexes.closeProject(projectOwner);
            projectOwner = indexes.openProject(project);
            analysis.adoptConfiguration(projectOwner, analysis.prepareConfiguration(projectOwner, configuration));
            secondDialect.close();
            assertEquals(expectedCaches, analysis.cachedSessions());
        } finally {
            analysis.closeSession(projectOwner);
            indexes.closeProject(projectOwner);
        }
    }

    @Test
    public void builtInJavaSessionCreatesFeaturesForPhysicalAndVirtualDocuments() {
        Project project = project();
        LanguageSupports.initialize();
        var support = LanguageSupportRegistry.getExpected(JavaLanguageSupport.LANGUAGE_ID);
        try {
            for (DocumentUri uri : List.of(DocumentUri.virtual("generated", "/Example.java"),
                DocumentUri.parse("jar:file:///libs/example.jar!/Example.java"),
                DocumentUri.inMemory(DocumentId.create()),
                DocumentUri.fromPath(tempDirectory.resolve("Example.java")))) {
                CodeEditorConfig config = CodeEditorConfig.fromDocument(project, uri, support, null);
                assertNotNull(config.completionProvider());
                assertNotNull(config.diagnosticsProvider());
                assertNotNull(config.signatureHelpProvider());
                assertNotNull(config.highlightingProvider());
            }
        } finally {
            LanguageSupportRegistry.runtime().closeProject(project);
        }
    }

    @Test
    public void independentContributionUsesEditorConfigurationWithoutOpeningAnEditor() {
        Project project = project();
        CompletionProvider completion = (snapshot, trigger) -> CompletionResult.empty();
        LanguageContribution contribution = new LanguageContribution() {
            @Override
            public LanguageId id() {
                return new LanguageId("test:independent");
            }
            @Override
            public String displayName() {
                return "Independent";
            }
            @Override
            public LanguageMatcher matcher() {
                return LanguageMatcher.extension("independent");
            }
            @Override
            public Set<CapabilityKey<?>> supportedCapabilities() {
                return Set.of(SstCapabilities.COMPLETION);
            }
            @Override
            public LanguageSession openSession(LanguageSessionContext context) {
                assertEquals(tempDirectory.toAbsolutePath().normalize(), context.projectRoot());
                return new LanguageSession(
                    CapabilityRegistry.builder().add(SstCapabilities.COMPLETION, completion).build());
            }
        };
        LanguageSupportRegistry.runtime().register("test-plugin", contribution);
        try {
            var support = LanguageSupportRegistry.find(tempDirectory.resolve("source.independent")).orElseThrow();
            CodeEditorConfig first = CodeEditorConfig.fromDocument(project,
                DocumentUri.virtual("generated", "/source.independent"), support, null);
            CodeEditorConfig second = CodeEditorConfig.fromLanguageSupport(project,
                tempDirectory.resolve("source.independent"), support, null);
            assertEquals("test:independent", first.languageId());
            assertSame(first.completionProvider(), second.completionProvider());
            assertNull(first.diagnosticsProvider());
            assertNull(first.signatureHelpProvider());
            LanguageSupportRegistry.runtime().unregisterOwner("test-plugin");
            assertThrows(CancellationException.class,
                () -> first.completionProvider().compute((DocumentSnapshot) null, 0));
        } finally {
            LanguageSupportRegistry.runtime().unregisterOwner("test-plugin");
            LanguageSupportRegistry.runtime().closeProject(project);
        }
    }

    private Project project() {
        return (Project) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Project.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPath" -> tempDirectory;
                case "getAlias" -> "Capability test";
                case "hasFacet" -> false;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "CapabilityTestProject";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
