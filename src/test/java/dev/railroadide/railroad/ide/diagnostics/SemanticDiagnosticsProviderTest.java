package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.ide.diagnostics.inspections.CoreNameResolutionInspection;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SemanticDiagnosticsProviderTest {

    @Test
    void coreSemanticInspectionIsRegisteredAndProducesDiagnostics() {
        JavaInspectionRuleProvider core = JavaInspectionRegistries.JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.get(CoreNameResolutionInspection.ID);
        assertNotNull(core);

        SemanticDiagnosticsProvider provider = new SemanticDiagnosticsProvider(Path.of("Example.java"));
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
        String id = "test:plugin-rule-provider-" + UUID.randomUUID();
        JavaInspectionRuleProvider provider = new JavaInspectionRuleProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<JavaInspectionRule> rules() {
                return List.of(new JavaInspectionRule() {
                    @Override
                    public String id() {
                        return "PLUGIN_RULE_WARNING";
                    }

                    @Override
                    public dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic.Severity defaultSeverity() {
                        return dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic.Severity.WARNING;
                    }

                    @Override
                    public String messageTemplate() {
                        return "Plugin rule warning";
                    }

                    @Override
                    public void evaluate(JavaRuleContext context, JavaInspectionRuleReporter reporter) {
                        reporter.reportMessage(context.syntaxTree().root(), "Plugin rule warning");
                    }
                });
            }
        };

        try {
            JavaInspectionRegistries.JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.register(id, provider);
            SemanticDiagnosticsProvider providerRunner = new SemanticDiagnosticsProvider(Path.of("Example.java"));
            List<EditorDiagnostic> diagnostics = providerRunner.compute("class Example {}");
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> "PLUGIN_RULE_WARNING".equals(diagnostic.code())));
            assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.kind() == Diagnostic.Kind.WARNING));
        } finally {
            if (JavaInspectionRegistries.JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.contains(id))
                JavaInspectionRegistries.JAVA_INSPECTION_RULE_PROVIDER_REGISTRY.unregister(id);
        }
    }

    @Test
    void supportsRuleSettingsOverridesAndDisabling() {
        try {
            JavaInspectionRuleSettings.setRuleEnabled("SEM_UNRESOLVED_NAME", false);
            SemanticDiagnosticsProvider provider = new SemanticDiagnosticsProvider(Path.of("Example.java"));
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
                    dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic.Severity.INFO);
            SemanticDiagnosticsProvider provider = new SemanticDiagnosticsProvider(Path.of("Example.java"));
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
}
