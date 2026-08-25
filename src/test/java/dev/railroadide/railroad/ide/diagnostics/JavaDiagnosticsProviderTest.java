package dev.railroadide.railroad.ide.diagnostics;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.WorkspaceModes;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.diagnostics.inspections.CoreNameResolutionInspection;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetType;
import dev.railroadide.railroad.vcs.git.GitManager;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class JavaDiagnosticsProviderTest {
    private static final String PLUGIN_RULE_PROVIDER_ID = "test:plugin-rule-provider";
    private static final String PLUGIN_RULE_ID = "PLUGIN_RULE_WARNING";

    @Test
    void coreSemanticInspectionIsRegisteredAndProducesDiagnostics() {
        JavaInspectionRuleProvider core = JavaInspectionRegistries.getRuleProvider(CoreNameResolutionInspection.ID);
        assertNotNull(core);

        var provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
        List<EditorDiagnostic> diagnostics = provider.compute("""
            class Example {
                void run() {
                    missing = 1;
                }
            }
            """);

        assertTrue(diagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code())));
    }

    @Test
    void runsRegisteredPluginRuleProviders() {
        String id = PLUGIN_RULE_PROVIDER_ID + "-" + UUID.randomUUID();
        JavaInspectionRuleProvider provider = new TestJavaInspectionRuleProvider(id);

        try {
            JavaInspectionRegistries.registerRuleProvider(id, provider);
            var providerRunner = new JavaDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> diagnostics = providerRunner.compute("class Example {}");
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> PLUGIN_RULE_ID.equals(diagnostic.code())));
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.WARNING));
        } finally {
            if (JavaInspectionRegistries.containsRuleProvider(id)) {
                JavaInspectionRegistries.unregisterRuleProvider(id);
            }
        }
    }

    @Test
    void supportsRuleSettingsOverridesAndDisabling() {
        try {
            JavaInspectionRuleSettings.setRuleEnabled("SEM_UNRESOLVED_NAME", false);
            var provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> disabledDiagnostics = provider.compute("""
                class Example {
                    void run() {
                        missing = 1;
                    }
                }
                """);
            assertFalse(
                disabledDiagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code())));
        } finally {
            JavaInspectionRuleSettings.resetAll();
        }

        try {
            JavaInspectionRuleSettings.setSeverityOverride("SEM_UNRESOLVED_NAME",
                SemanticDiagnostic.Severity.INFO);
            var provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> overriddenDiagnostics = provider.compute("""
                class Example {
                    void run() {
                        missing = 1;
                    }
                }
                """);
            EditorDiagnostic unresolved = overriddenDiagnostics.stream()
                .filter(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code()))
                .findFirst()
                .orElse(null);
            assertNotNull(unresolved);
            assertEquals(Diagnostic.Kind.NOTE, unresolved.kind());
        } finally {
            JavaInspectionRuleSettings.resetAll();
        }
    }

    @Test
    void exportPathResolvesGitCommandBuilderCallsForSingleFile() throws Exception {
        ensureJavaLanguageSupportRegistered();
        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        Path file = projectRoot.resolve("src/main/java/dev/railroadide/railroad/vcs/git/GitCommands.java");
        ProjectDiagnosticsContext context = ProjectDiagnosticsContext.create(new TestProject(projectRoot));
        var provider = new JavaDiagnosticsProvider(context, file);

        List<String> unresolved = provider.compute(Files.readString(file)).stream()
            .filter(diagnostic -> "SEM_UNRESOLVED_CALL".equals(diagnostic.code()))
            .filter(diagnostic -> diagnostic.getMessage(null).contains("'addArgs'")
                || diagnostic.getMessage(null).contains("'build'"))
            .map(diagnostic -> "line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null))
            .toList();

        assertTrue(unresolved.isEmpty(), () -> String.join(System.lineSeparator(), unresolved));
    }

    private static void ensureJavaLanguageSupportRegistered() {
        WorkspaceModes.initialize();

        if (!LanguageSupportRegistry.contains(JavaLanguageSupport.LANGUAGE_ID)) {
            LanguageSupportRegistry.register(new JavaLanguageSupport());
        }

        if (!Services.PROJECT_LANGUAGE_INDEX_SERVICE.hasIndexer(JavaLanguageSupport.LANGUAGE_ID)) {
            Services.PROJECT_LANGUAGE_INDEX_SERVICE.registerIndexer(new JavaLanguageSupport().createIndexer());
        }
    }

    private static final class TestJavaInspectionRuleProvider implements JavaInspectionRuleProvider {
        private final String id;

        private TestJavaInspectionRuleProvider(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public List<JavaInspectionRule> rules() {
            return List.of(new TestJavaInspectionRule());
        }
    }

    private static final class TestJavaInspectionRule implements JavaInspectionRule {
        @Override
        public String id() {
            return PLUGIN_RULE_ID;
        }

        @Override
        public SemanticDiagnostic.Severity defaultSeverity() {
            return SemanticDiagnostic.Severity.WARNING;
        }

        @Override
        public String messageTemplate() {
            return "Plugin rule warning";
        }

        @Override
        public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
            reporter.reportMessage(context.syntaxTree().root(), "Plugin rule warning");
        }
    }

    private record TestProject(Path path) implements Project {
        @Override
        public Path getPath() {
            return path;
        }

        @Override
        public String getAlias() {
            return path.getFileName() == null ? path.toString() : path.getFileName().toString();
        }

        @Override
        public void setAlias(String alias) {
            throw unsupported();
        }

        @Override
        public boolean hasFacet(FacetType<?> type) {
            return false;
        }

        @Override
        public <D> Optional<Facet<D>> getFacet(FacetType<D> type) {
            return Optional.empty();
        }

        @Override
        public void open(Stage stage) {
            throw unsupported();
        }

        @Override
        public void close() {
            throw unsupported();
        }

        @Override
        public String getId() {
            return getPathString();
        }

        @Override
        public long getLastOpened() {
            return 0L;
        }

        @Override
        public void setLastOpened(long timestamp) {
            throw unsupported();
        }

        @Override
        public List<Facet<?>> getFacets() {
            return List.of();
        }

        @Override
        public CompletableFuture<Runnable> build(JDK jdk) {
            throw unsupported();
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void setDescription(String description) {
            throw unsupported();
        }

        @Override
        public License getLicense() {
            throw unsupported();
        }

        @Override
        public void setLicense(License license) {
            throw unsupported();
        }

        @Override
        public GitManager getGitManager() {
            throw unsupported();
        }

        @Override
        public RunConfigurationManager getRunConfigManager() {
            throw unsupported();
        }

        @Override
        public DebuggingManager getDebuggingManager() {
            throw unsupported();
        }

        @Override
        public ProjectDataStore getDataStore() {
            throw unsupported();
        }

        @Override
        public GradleManager getGradleManager() {
            throw unsupported();
        }

        @Override
        public Image getIcon() {
            throw unsupported();
        }

        @Override
        public void setIcon(Image icon) {
            throw unsupported();
        }

        @Override
        public JsonObject toJson() {
            throw unsupported();
        }

        @Override
        public void fromJson(JsonObject json) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("Test project only exposes the project path.");
        }
    }
}
