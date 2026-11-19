package dev.railroadide.railroad.ide.diagnostics;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jetbrains.annotations.NotNull;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;

import dev.railroadide.railroad.ide.diagnostics.DiagnosticSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Diagnostics provider backed by Eclipse JDT's parser.
 */
public record JdtDiagnosticsProvider() implements DiagnosticsProvider<TextEditorDiagnostic> {
    @Override
    public @NotNull List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
        Optional<String> snapshotText = TextDocumentSnapshot.unwrap(
            snapshot, JavaLanguageSupport.LANGUAGE_ID);
        if (snapshotText.isEmpty())
            return List.of();

        char[] source = snapshotText.get().toCharArray();

        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setStatementsRecovery(true);
        parser.setSource(source);
        parser.setUnitName(compilationUnitName(snapshot.uri()));

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);

        return toDiagnostics(unit.getProblems(), source, snapshot);
    }

    /**
     * Derives a compilation-unit filename from a physical or virtual document URI.
     *
     * @param uri document location, including opaque archive-entry URIs
     * @return the final path segment, or {@code Snapshot.java} when no filename is available
     */
    public static String compilationUnitName(DocumentUri uri) {
        return uri.fileName().orElse("Snapshot.java");
    }

    private static List<TextEditorDiagnostic> toDiagnostics(
        IProblem[] problems,
        char[] source,
        DocumentSnapshot snapshot
    ) {
        if (problems == null || problems.length == 0)
            return List.of();

        List<TextEditorDiagnostic> diagnostics = new ArrayList<>(problems.length);
        for (IProblem problem : problems) {
            DiagnosticSeverity kind = problem.isError()
                ? DiagnosticSeverity.ERROR
                : (problem.isWarning() ? DiagnosticSeverity.WARNING : DiagnosticSeverity.INFORMATION);
            if (kind == DiagnosticSeverity.INFORMATION)
                continue;

            int start = Math.max(0, problem.getSourceStart());
            int end = Math.min(source.length, problem.getSourceEnd() + 1);
            String message = problem.getMessage();
            String code = problem.getID() == 0 ? null : Integer.toString(problem.getID());

            diagnostics.add(
                new EditorDiagnostic.TextEditorDiagnostic(
                    TextLocation.from((TextDocumentSnapshot) snapshot, start, end),
                    kind,
                    code,
                    message, "railroad:jdt", DiagnosticOrigin.SYNTAX, Set.of(), List.of(), null));
        }

        return List.copyOf(diagnostics);
    }
}
