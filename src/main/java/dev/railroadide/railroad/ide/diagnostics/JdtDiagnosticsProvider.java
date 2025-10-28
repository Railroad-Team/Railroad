package dev.railroadide.railroad.ide.diagnostics;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jetbrains.annotations.NotNull;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Diagnostics provider backed by Eclipse JDT's parser.
 */
public record JdtDiagnosticsProvider(Path filePath) implements DiagnosticsProvider {
    @Override
    public @NotNull List<EditorDiagnostic> compute(String document) {
        if (document == null || document.isEmpty())
            return List.of();

        char[] source = document.toCharArray();

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
        JavaFileObject sourceFile = new SimpleJavaFileObject(filePath.toUri(), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return document;
            }
        };

        return toDiagnostics(unit.getProblems(), source, sourceFile);
    }

    private static List<EditorDiagnostic> toDiagnostics(IProblem[] problems, char[] source, JavaFileObject sourceFile) {
        if (problems == null || problems.length == 0)
            return List.of();

        List<EditorDiagnostic> diagnostics = new ArrayList<>(problems.length);
        for (IProblem problem : problems) {
            Diagnostic.Kind kind = problem.isError()
                ? Diagnostic.Kind.ERROR
                : (problem.isWarning() ? Diagnostic.Kind.WARNING : Diagnostic.Kind.OTHER);
            if (kind == Diagnostic.Kind.OTHER)
                continue;

            int start = Math.max(0, problem.getSourceStart());
            int end = Math.min(source.length, problem.getSourceEnd() + 1);
            long line = problem.getSourceLineNumber();
            long column = computeColumn(source, start);
            String message = problem.getMessage();
            String code = problem.getID() == 0 ? null : Integer.toString(problem.getID());

            diagnostics.add(new EditorDiagnostic(kind, start, end, line, column, message, code, sourceFile));
        }

        return diagnostics;
    }

    private static long computeColumn(char[] source, int position) {
        int column = 1;
        for (int i = position - 1; i >= 0; i--) {
            char c = source[i];
            if (c == '\n' || c == '\r')
                break;

            column++;
        }

        return column;
    }
}
