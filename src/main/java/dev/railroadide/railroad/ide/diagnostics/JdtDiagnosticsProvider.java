package dev.railroadide.railroad.ide.diagnostics;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jetbrains.annotations.NotNull;

import dev.railroadide.railroad.ide.diagnostics.EditorDiagnostic.TextEditorDiagnostic;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.sst.document.api.DocumentSnapshot;
import dev.railroadide.railroad.ide.sst.document.api.Location.TextLocation;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Diagnostics provider backed by Eclipse JDT's parser.
 */
public record JdtDiagnosticsProvider(Path filePath) implements DiagnosticsProvider<TextEditorDiagnostic> {
    @Override
    public @NotNull List<TextEditorDiagnostic> compute(DocumentSnapshot snapshot) {
        Optional<String> snapshotText = TextDocumentSnapshot.unwrap(snapshot, new JavaLanguageSupport());
        if (snapshotText.isEmpty())
            return List.of();

        char[] source = snapshotText.get().toCharArray();

        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setBindingsRecovery(false);
        parser.setStatementsRecovery(true);
        parser.setSource(source);
        parser.setUnitName(filePath.getFileName().toString());

        Map<String, String> options = JavaCore.getOptions();
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        parser.setCompilerOptions(options);

        CompilationUnit unit = (CompilationUnit) parser.createAST(null);

        return toDiagnostics(unit.getProblems(), source, snapshot);
    }

    private static List<TextEditorDiagnostic> toDiagnostics(IProblem[] problems, char[] source, DocumentSnapshot snapshot) {
        if (problems == null || problems.length == 0)
            return List.of();

        List<TextEditorDiagnostic> diagnostics = new ArrayList<>(problems.length);
        for (IProblem problem : problems) {
            Diagnostic.Kind kind = problem.isError()
                ? Diagnostic.Kind.ERROR
                : (problem.isWarning() ? Diagnostic.Kind.WARNING : Diagnostic.Kind.OTHER);
            if (kind == Diagnostic.Kind.OTHER)
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
                    message
                )
            );
        }

        return diagnostics;
    }
}
