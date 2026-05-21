package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.impl.java.JavaSemanticAnalyzer;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionReporter;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import dev.railroadide.railroad.plugin.spi.inspection.JavaInspectionRuleProvider;
import dev.railroadide.railroad.plugin.spi.inspection.JavaRuleContext;
import org.jetbrains.annotations.NotNull;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Diagnostics provider backed by the SST semantic analyzer.
 */
public record JavaDiagnosticsProvider(Project project, Path filePath) implements DiagnosticsProvider {
    public JavaDiagnosticsProvider(Path filePath) {
        this(null, filePath);
    }

    @Override
    public @NotNull List<EditorDiagnostic> compute(String document) {
        if (document == null || document.isEmpty())
            return List.of();

        SemanticModel semanticModel;
        if (project != null) {
            JavaProjectSemanticIndex projectIndex =
                Services.PROJECT_LANGUAGE_INDEX_SERVICE.indexTyped(project.getPath(), JavaLanguageSupport.LANGUAGE_ID);
            semanticModel = projectIndex == null
                    ? JavaSemanticAnalyzer.analyzeFacts(document)
                    : JavaSemanticAnalyzer.analyzeFacts(document, projectIndex);
        } else {
            semanticModel = JavaSemanticAnalyzer.analyzeFacts(document);
        }

        List<SemanticDiagnostic> semanticDiagnostics = runRegisteredInspections(document, semanticModel);
        char[] source = document.toCharArray();
        JavaFileObject sourceFile = new SimpleJavaFileObject(filePath.toUri(), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return document;
            }
        };

        List<EditorDiagnostic> diagnostics = new ArrayList<>();
        for (SemanticDiagnostic diagnostic : semanticDiagnostics) {
            Diagnostic.Kind kind = switch (diagnostic.severity()) {
                case ERROR -> Diagnostic.Kind.ERROR;
                case WARNING -> Diagnostic.Kind.WARNING;
                case INFO -> Diagnostic.Kind.NOTE;
            };

            int start = Math.clamp(diagnostic.startOffset(), 0, source.length);
            int end = Math.clamp(diagnostic.endOffset(), start, source.length);
            long line = computeLine(source, start);
            long column = computeColumn(source, start);
            diagnostics.add(new EditorDiagnostic(
                kind,
                start,
                end,
                line,
                column,
                diagnostic.message(),
                diagnostic.code(),
                sourceFile
            ));
        }

        return List.copyOf(diagnostics);
    }

    private List<SemanticDiagnostic> runRegisteredInspections(String document, SemanticModel semanticModel) {
        List<SemanticDiagnostic> diagnostics = new ArrayList<>();
        JavaRuleContext context = new JavaRuleContext(filePath, document, semanticModel);
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

    private static long computeLine(char[] source, int position) {
        long line = 1;
        int bound = Math.clamp(source.length, 0, position);
        for (int index = 0; index < bound; index++) {
            if (source[index] == '\n')
                line++;
        }
        return line;
    }

    private static long computeColumn(char[] source, int position) {
        int column = 1;
        int index = Math.clamp(source.length, 0, position) - 1;
        for (; index >= 0; index--) {
            char ch = source[index];
            if (ch == '\n' || ch == '\r')
                break;
            column++;
        }
        return column;
    }
}
