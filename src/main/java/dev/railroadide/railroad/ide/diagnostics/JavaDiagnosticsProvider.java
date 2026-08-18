package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.JavaSymbolIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Diagnostics provider backed by the SST semantic analyzer.
 */
public record JavaDiagnosticsProvider(Project project, Path filePath, @Nullable JavaSymbolIndex projectIndex) implements DiagnosticsProvider<TextEditorDiagnostic> {
    public JavaDiagnosticsProvider(Path filePath) {
        this(null, filePath, null);
    }

    public JavaDiagnosticsProvider(Project project, Path filePath) {
        this(project, filePath, null);
    }

    public JavaDiagnosticsProvider(ProjectDiagnosticsContext context, Path filePath) {
        this(context.project(), filePath, context.javaSymbolIndex());
    }

    @Override
    public @NotNull List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
        Optional<String> snapshotText = TextDocumentSnapshot.unwrap(snapshot, new JavaLanguageSupport());
        if (snapshotText.isEmpty())
            return List.of();

        String document = snapshotText.get();

        SemanticModel semanticModel;
        JavaSymbolIndex symbolIndex = projectIndex;
        if (symbolIndex != null) {
            semanticModel = JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        } else if (project != null) {
            symbolIndex = JavaLanguageSupport.analysisContextProvider().index(project);
            semanticModel = symbolIndex == null
                ? JavaSemanticAnalyzer.analyzeFacts(document)
                : JavaSemanticAnalyzer.analyzeFacts(document, symbolIndex);
        } else {
            semanticModel = JavaSemanticAnalyzer.analyzeFacts(document);
        }

        List<SemanticDiagnostic> semanticDiagnostics = runRegisteredInspections(document, semanticModel, symbolIndex);
        char[] source = document.toCharArray();

        List<TextEditorDiagnostic> diagnostics = new ArrayList<>();
        for (SemanticDiagnostic diagnostic : semanticDiagnostics) {
            Diagnostic.Kind kind = switch (diagnostic.severity()) {
                case ERROR -> Diagnostic.Kind.ERROR;
                case WARNING -> Diagnostic.Kind.WARNING;
                case INFO -> Diagnostic.Kind.NOTE;
            };

            int start = Math.clamp(diagnostic.startOffset(), 0, source.length);
            int end = Math.clamp(diagnostic.endOffset(), start, source.length);
            diagnostics.add(
                new TextEditorDiagnostic(
                    TextLocation.from((TextDocumentSnapshot) snapshot, start, end),
                    kind,
                    diagnostic.code(),
                    diagnostic.message()
                )
            );
        }

        return List.copyOf(diagnostics);
    }

    private List<SemanticDiagnostic> runRegisteredInspections(String document, SemanticModel semanticModel, JavaSymbolIndex symbolIndex) {
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaRuleContext context = new JavaRuleContext(filePath, document, semanticModel, symbolIndex);
        JavaInspectionReporter reporter = diagnostic -> diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));

        for (JavaInspectionRuleProvider provider : sortedRuleProviders()) {
            if (provider == null)
                continue;

            try {
                JavaInspectionRuleEngine.runRules(provider, context, reporter);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Plugin Java inspection rule provider '{}' failed for {}", provider.id(), filePath, exception);
            }
        }

        return List.copyOf(diagnostics);
    }

    private static List<JavaInspectionRuleProvider> sortedRuleProviders() {
        return JavaInspectionRegistries.ruleProviderEntries().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(java.util.Map.Entry::getValue)
                .toList();
    }
}
