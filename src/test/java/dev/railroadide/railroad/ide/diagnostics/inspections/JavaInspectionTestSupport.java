package dev.railroadide.railroad.ide.diagnostics.inspections;

import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleEngine;
import dev.railroadide.railroad.ide.diagnostics.JavaInspectionRuleSettings;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRule;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class JavaInspectionTestSupport {
    private JavaInspectionTestSupport() {
    }

    public static List<SemanticDiagnostic> runProvider(JavaInspectionRuleProvider provider, String document) {
        return runProvider(provider, Path.of("Example.java"), document);
    }

    public static List<SemanticDiagnostic> runProvider(
        JavaInspectionRuleProvider provider,
        Path filePath,
        String document
    ) {
        return runProvider(provider, filePath, document, null);
    }

    public static List<SemanticDiagnostic> runProvider(
        JavaInspectionRuleProvider provider,
        Path filePath,
        String document,
        JavaSymbolIndex symbolIndex
    ) {
        JavaInspectionRuleSettings.resetAll();
        var model = symbolIndex == null
            ? JavaSemanticAnalyzer.analyzeFacts(document)
            : JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        var context = new JavaRuleContext(filePath, document, model, symbolIndex);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaInspectionReporter reporter = diagnostics::add;
        JavaInspectionRuleEngine.runRules(provider, context, reporter);
        return List.copyOf(diagnostics);
    }

    public static List<SemanticDiagnostic> runProviders(
        List<? extends JavaInspectionRuleProvider> providers,
        Path filePath,
        String document,
        JavaSymbolIndex symbolIndex
    ) {
        JavaInspectionRuleSettings.resetAll();
        var model = JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        var context = new JavaRuleContext(filePath, document, model, symbolIndex);
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaInspectionReporter reporter = diagnostics::add;
        providers.forEach(provider -> JavaInspectionRuleEngine.runRules(provider, context, reporter));
        return List.copyOf(diagnostics);
    }

    public static void assertRuleIds(JavaInspectionRuleProvider provider, Set<String> expectedIds) {
        Set<String> actual = provider.rules().stream()
            .map(JavaInspectionRule::id)
            .collect(Collectors.toSet());
        assertEquals(expectedIds, actual);
    }
}
