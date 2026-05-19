package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.diagnostics.inspections.CoreNameResolutionInspection;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JavaDiagnosticsProviderTest {
    private static final String PLUGIN_RULE_PROVIDER_ID = "test:plugin-rule-provider";
    private static final String PLUGIN_RULE_ID = "PLUGIN_RULE_WARNING";

    @Test
    void coreSemanticInspectionIsRegisteredAndProducesDiagnostics() {
        JavaInspectionRuleProvider core = JavaInspectionRegistries.getRuleProvider(CoreNameResolutionInspection.ID);
        assertNotNull(core);

        JavaDiagnosticsProvider provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
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
            JavaDiagnosticsProvider providerRunner = new JavaDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> diagnostics = providerRunner.compute("class Example {}");
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> PLUGIN_RULE_ID.equals(diagnostic.code())));
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.WARNING));
        } finally {
            if (JavaInspectionRegistries.containsRuleProvider(id))
                JavaInspectionRegistries.unregisterRuleProvider(id);
        }
    }

    @Test
    void supportsRuleSettingsOverridesAndDisabling() {
        try {
            JavaInspectionRuleSettings.setRuleEnabled("SEM_UNRESOLVED_NAME", false);
            JavaDiagnosticsProvider provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> disabledDiagnostics = provider.compute("""
                    class Example {
                        void run() {
                            missing = 1;
                        }
                    }
                    """);
            assertFalse(disabledDiagnostics.stream().anyMatch(diagnostic -> "SEM_UNRESOLVED_NAME".equals(diagnostic.code())));
        } finally {
            JavaInspectionRuleSettings.resetAll();
        }

        try {
            JavaInspectionRuleSettings.setSeverityOverride("SEM_UNRESOLVED_NAME",
                    SemanticDiagnostic.Severity.INFO);
            JavaDiagnosticsProvider provider = new JavaDiagnosticsProvider(Path.of("Example.java"));
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
}
